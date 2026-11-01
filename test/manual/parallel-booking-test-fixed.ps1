#Requires -Version 5.1
<#
Hotel Booking / Stage 10: manual Saga concurrency + autoSelect concurrency.
Preserves original TEST 1-4 and adds AUTO TEST 5-6.
Windows PowerShell 5.1, PowerShell 7; no external modules.

Examples (from a Windows cmd or IntelliJ terminal):
  run-parallel-booking-test.cmd -RoomId 2
  run-parallel-booking-test.cmd -RoomId 2 -HotelId 1 -StressCount 10
  run-parallel-booking-test.cmd -RoomId 2 -HotelId 1 -StartDate 2035-04-01

Requirements: Gateway/Booking/Hotel/Eureka running; ADMIN JWT; RoomId is an
active, unbooked test room; HotelId has at least two available rooms.
The script creates real reservations; use a test hotel and future dates.
#>
param(
    [int]$RoomId = 2,
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$StressCount = 10,
    [string]$StartDate = '',
    [long]$HotelId = 0
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

if ($RoomId -le 0) { throw 'RoomId must be positive.' }
if ($HotelId -lt 0) { throw 'HotelId cannot be negative.' }
if ($StressCount -lt 2 -or $StressCount -gt 50) {
    throw 'StressCount must be between 2 and 50.'
}

$token = $env:HOTEL_BOOKING_ADMIN_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $token = Read-Host 'Paste adminToken (without Bearer)'
}
$token = $token.Trim()
if ($token -match '^Bearer\s+(.+)$') { $token = $Matches[1] }
if ([string]::IsNullOrWhiteSpace($token)) { throw 'Token is empty.' }

# Fresh future dates for each run unless StartDate is supplied.
if ([string]::IsNullOrWhiteSpace($StartDate)) {
    $baseDate = ([datetime]'2035-01-01').AddDays((Get-Random -Minimum 0 -Maximum 3000))
} else {
    $baseDate = [datetime]::ParseExact(
        $StartDate,
        'yyyy-MM-dd',
        [System.Globalization.CultureInfo]::InvariantCulture
    )
}
if ($baseDate.Date -le (Get-Date).Date) {
    throw 'StartDate must be in the future.'
}

$rootUrl = $BaseUrl.TrimEnd('/')
$bookingsUrl = $rootUrl + '/api/bookings'
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.MaxConnectionsPerServer = 64
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(90)
$client.DefaultRequestHeaders.Authorization = `
    [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)

function New-ManualBookingJson {
    param([datetime]$CheckIn)
    return (@{
        roomId   = $RoomId
        checkIn  = $CheckIn.ToString('yyyy-MM-dd')
        checkOut = $CheckIn.AddDays(3).ToString('yyyy-MM-dd')
    } | ConvertTo-Json -Compress)
}

function New-AutoBookingJson {
    param([datetime]$CheckIn, [long]$IgnoredRoomId = 0)
    $payload = @{
        hotelId    = $HotelId
        autoSelect = $true
        checkIn    = $CheckIn.ToString('yyyy-MM-dd')
        checkOut   = $CheckIn.AddDays(3).ToString('yyyy-MM-dd')
    }
    if ($IgnoredRoomId -ne 0) {
        # Stage 10: request.roomId must be ignored in autoSelect mode.
        $payload.roomId = $IgnoredRoomId
    }
    return ($payload | ConvertTo-Json -Compress)
}

function Get-ApiJson {
    param([string]$Path)
    $response = $null
    try {
        $response = $client.GetAsync($rootUrl + $Path).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = $null
        try { if ($raw) { $data = ConvertFrom-Json -InputObject $raw } } catch { }
        return [pscustomobject]@{
            Http = [int]$response.StatusCode
            Data = $data
            Raw  = $raw
        }
    } finally {
        if ($null -ne $response) { $response.Dispose() }
    }
}

function Read-BookingResponse {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [string]$Label
    )
    try {
        $raw = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = $null
        try { if ($raw) { $data = ConvertFrom-Json -InputObject $raw } } catch { }
        $http = [int]$Response.StatusCode
        $id = if ($null -ne $data) { $data.id } else { $null }
        $roomIdResult = if ($null -ne $data) { $data.roomId } else { $null }
        $status = if ($null -ne $data) { $data.status } else { $null }
        $code = if ($null -ne $data) { $data.code } else { $null }
        Write-Host ('{0}: HTTP={1}, bookingId={2}, roomId={3}, status={4}, error={5}' -f `
            $Label, $http, $id, $roomIdResult, $status, $code)
        if ($http -ge 400) {
            $detail = if ($null -ne $data -and $data.message) { $data.message } else { $raw }
            if ($detail) { Write-Host ('  Detail: ' + $detail) }
        }
        return [pscustomobject]@{
            Http = $http
            Id = $id
            RoomId = $roomIdResult
            Status = $status
            Code = $code
        }
    } finally {
        $Response.Dispose()
    }
}

function Invoke-BookingBatch {
    param(
        [string[]]$Jsons,
        [string[]]$Keys,
        [string]$LabelPrefix
    )

    if ($Jsons.Count -ne $Keys.Count) {
        throw 'Each request must have a JSON body and an idempotency key.'
    }
    $pending = @()
    try {
        # Send ALL requests before awaiting any one response: real concurrency.
        for ($i = 0; $i -lt $Keys.Count; $i++) {
            $req = [System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::Post, $bookingsUrl
            )
            [void]$req.Headers.TryAddWithoutValidation('Idempotency-Key', $Keys[$i])
            $req.Content = [System.Net.Http.StringContent]::new(
                $Jsons[$i], [System.Text.Encoding]::UTF8, 'application/json'
            )
            $pending += [pscustomobject]@{
                Request = $req
                Task = $client.SendAsync($req)
            }
        }

        for ($i = 0; $i -lt $pending.Count; $i++) {
            try {
                $response = $pending[$i].Task.GetAwaiter().GetResult()
                Read-BookingResponse -Response $response `
                    -Label ('{0} {1}' -f $LabelPrefix, ($i + 1))
            } catch {
                Write-Host ('{0} {1}: NETWORK/CLIENT ERROR: {2}' -f `
                    $LabelPrefix, ($i + 1), $_.Exception.Message)
                [pscustomobject]@{
                    Http = 0; Id = $null; RoomId = $null
                    Status = $null; Code = 'CLIENT_ERROR'
                }
            }
        }
    } finally {
        foreach ($item in $pending) {
            $item.Request.Dispose()
        }
    }
}

function Test-Confirmed {
    param($Result)
    return ($null -ne $Result -and $Result.Http -eq 201 -and
        $Result.Status -eq 'CONFIRMED' -and
        $null -ne $Result.Id -and $null -ne $Result.RoomId)
}

function Test-PairWithSameKey {
    param([object[]]$Initial, $Replay)

    $confirmed = @($Initial | Where-Object { Test-Confirmed $_ })
    $inProgress = @($Initial | Where-Object {
        $_.Http -eq 409 -and $_.Code -eq 'REQUEST_IN_PROGRESS'
    })
    $initialValid = (
        ($confirmed.Count -eq 2 -and
            [string]$confirmed[0].Id -eq [string]$confirmed[1].Id -and
            [string]$confirmed[0].RoomId -eq [string]$confirmed[1].RoomId) -or
        ($confirmed.Count -eq 1 -and $inProgress.Count -eq 1)
    )
    $replayValid = (Test-Confirmed $Replay) -and $confirmed.Count -gt 0 -and
        ([string]$Replay.Id -eq [string]$confirmed[0].Id) -and
        ([string]$Replay.RoomId -eq [string]$confirmed[0].RoomId)

    return ($Initial.Count -eq 2 -and $initialValid -and $replayValid)
}

function Write-TestResult {
    param([int]$Number, [bool]$Passed, [string]$Description)
    if ($Passed) {
        Write-Host ('PASS TEST {0}: {1}' -f $Number, $Description) -ForegroundColor Green
    } else {
        Write-Host ('FAIL TEST {0}: {1}' -f $Number, $Description) -ForegroundColor Red
        $script:allPassed = $false
    }
}

$allPassed = $true
try {
    Write-Host ('Gateway: ' + $rootUrl)
    Write-Host ('Manual test RoomId: ' + $RoomId)
    Write-Host ('Tests use fresh future dates and CREATE REAL BOOKINGS.')

    # Check manual room exists; if HotelId was omitted, discover it from room.
    $roomProbe = Get-ApiJson -Path ('/api/rooms/{0}' -f $RoomId)
    if ($roomProbe.Http -ne 200 -or $null -eq $roomProbe.Data) {
        throw ('RoomId={0} cannot be read via Gateway (HTTP={1}). ' +
               'Use -RoomId with an existing test room. Response: {2}' -f `
               $RoomId, $roomProbe.Http, $roomProbe.Raw)
    }
    if ($roomProbe.Data.available -ne $true) {
        throw ('RoomId={0} is disabled (available=false).' -f $RoomId)
    }
    if ($HotelId -eq 0) { $HotelId = [long]$roomProbe.Data.hotelId }
    if ($HotelId -le 0) { throw 'HotelId is missing. Specify -HotelId <id>.' }
    Write-Host ('AUTO test HotelId: ' + $HotelId)

    # TEST 1: original SAME key, two parallel manual requests + replay.
    $date1 = $baseDate
    $json1 = New-ManualBookingJson -CheckIn $date1
    $key1 = [guid]::NewGuid().ToString()
    Write-Host "`n### TEST 1 - MANUAL: SAME key, 2 parallel requests + replay"
    Write-Host ('Dates: {0} - {1}' -f $date1.ToString('yyyy-MM-dd'), $date1.AddDays(3).ToString('yyyy-MM-dd'))
    $first = @(Invoke-BookingBatch -Jsons @($json1, $json1) -Keys @($key1, $key1) -LabelPrefix 'Parallel')
    $replay = @(Invoke-BookingBatch -Jsons @($json1) -Keys @($key1) -LabelPrefix 'Replay')[0]
    Write-TestResult 1 (Test-PairWithSameKey -Initial $first -Replay $replay) 'Manual idempotency, same booking ID and room.'

    # TEST 2: original DIFFERENT keys, same manual room & dates.
    $date2 = $baseDate.AddDays(14)
    $json2 = New-ManualBookingJson -CheckIn $date2
    Write-Host "`n### TEST 2 - MANUAL: DIFFERENT keys, 2 parallel requests"
    $second = @(Invoke-BookingBatch -Jsons @($json2, $json2) `
        -Keys @([guid]::NewGuid().ToString(), [guid]::NewGuid().ToString()) -LabelPrefix 'Parallel')
    $winners = @($second | Where-Object { Test-Confirmed $_ })
    $losers = @($second | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'ROOM_ALREADY_BOOKED' })
    Write-TestResult 2 ($second.Count -eq 2 -and $winners.Count -eq 1 -and $losers.Count -eq 1) `
        'Exactly one reservation for the same manual room.'

    # TEST 3: original NEW key on already occupied manual dates.
    Write-Host "`n### TEST 3 - MANUAL: NEW key for occupied dates"
    $third = @(Invoke-BookingBatch -Jsons @($json2) `
        -Keys @([guid]::NewGuid().ToString()) -LabelPrefix 'Conflict')[0]
    Write-TestResult 3 ($third.Http -eq 409 -and $third.Code -eq 'ROOM_ALREADY_BOOKED') `
        'Occupied manual room must be rejected.'

    # TEST 4: original STRESS test, N keys, same manual room and dates.
    $date4 = $baseDate.AddDays(28)
    $json4 = New-ManualBookingJson -CheckIn $date4
    $keys4 = @((1..$StressCount) | ForEach-Object { [guid]::NewGuid().ToString() })
    $bodies4 = @((1..$StressCount) | ForEach-Object { $json4 })
    Write-Host "`n### TEST 4 - MANUAL STRESS: $StressCount DIFFERENT keys, parallel requests"
    $stress = @(Invoke-BookingBatch -Jsons $bodies4 -Keys $keys4 -LabelPrefix 'Stress')
    $stressWinners = @($stress | Where-Object { Test-Confirmed $_ })
    $stressConflicts = @($stress | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'ROOM_ALREADY_BOOKED' })
    Write-TestResult 4 ($stress.Count -eq $StressCount -and $stressWinners.Count -eq 1 -and `
        $stressConflicts.Count -eq ($StressCount - 1)) `
        'Exactly one manual reservation under stress.'

    # AUTO preflight: Hotel must offer at least two eligible rooms on each date.
    $date5 = $baseDate.AddDays(42)
    $date6 = $baseDate.AddDays(56)
    $candidates = @()
    $autoReady = $true
    foreach ($date in @($date5, $date6)) {
        $path = '/api/rooms/recommend?hotelId={0}&checkIn={1}&checkOut={2}' -f `
            $HotelId, $date.ToString('yyyy-MM-dd'), $date.AddDays(3).ToString('yyyy-MM-dd')
        $probe = Get-ApiJson -Path $path
        if ($probe.Http -ne 200) {
            Write-Host ('AUTO preflight: HTTP {0} for {1}; {2}' -f `
                $probe.Http, $date.ToString('yyyy-MM-dd'), $probe.Raw) -ForegroundColor Red
            $autoReady = $false
            continue
        }
        $available = @($probe.Data | Where-Object { $null -ne $_ -and $null -ne $_.id })
        Write-Host ('AUTO preflight {0}: {1} eligible rooms' -f `
            $date.ToString('yyyy-MM-dd'), $available.Count)
        if ($available.Count -lt 2) {
            Write-Host 'AUTO tests need an active hotel with at least 2 free rooms.' -ForegroundColor Yellow
            $autoReady = $false
        }
    }

    if ($autoReady) {
        # TEST 5: different keys, autoSelect=true, two rooms must be reserved.
        Write-Host "`n### TEST 5 - AUTO: DIFFERENT keys, 2 parallel requests"
        $auto5 = New-AutoBookingJson -CheckIn $date5
        $pair5 = @(Invoke-BookingBatch -Jsons @($auto5, $auto5) `
            -Keys @([guid]::NewGuid().ToString(), [guid]::NewGuid().ToString()) `
            -LabelPrefix 'Auto')
        $test5Passed = ($pair5.Count -eq 2 -and
            (Test-Confirmed $pair5[0]) -and (Test-Confirmed $pair5[1]) -and
            ([string]$pair5[0].Id -ne [string]$pair5[1].Id) -and
            ([string]$pair5[0].RoomId -ne [string]$pair5[1].RoomId))
        Write-TestResult 5 $test5Passed 'Two AUTO bookings, different booking IDs and room IDs.'

        # TEST 6: same key, autoSelect=true, different ignored roomId values.
        # Replay has no roomId, so the fingerprint must still be equal.
        Write-Host "`n### TEST 6 - AUTO: SAME key, 2 parallel requests + replay"
        $key6 = [guid]::NewGuid().ToString()
        $autoA = New-AutoBookingJson -CheckIn $date6 -IgnoredRoomId 999999
        $autoB = New-AutoBookingJson -CheckIn $date6 -IgnoredRoomId 888888
        $autoReplay = New-AutoBookingJson -CheckIn $date6
        $pair6 = @(Invoke-BookingBatch -Jsons @($autoA, $autoB) `
            -Keys @($key6, $key6) -LabelPrefix 'Auto')
        $replay6 = @(Invoke-BookingBatch -Jsons @($autoReplay) -Keys @($key6) `
            -LabelPrefix 'Auto replay')[0]
        Write-TestResult 6 (Test-PairWithSameKey -Initial $pair6 -Replay $replay6) `
            'Same AUTO booking ID and room; ignored roomId does not change fingerprint.'
    } else {
        Write-Host "`nFAIL TEST 5-6: AUTO prerequisites not met." -ForegroundColor Red
        $allPassed = $false
    }
} catch {
    $allPassed = $false
    Write-Host ('FAIL: ' + $_.Exception.Message) -ForegroundColor Red
} finally {
    $client.Dispose()
    $handler.Dispose()
}

Write-Host "`n### OVERALL RESULT"
if ($allPassed) {
    Write-Host 'PASS: all 6 tests passed.' -ForegroundColor Green
    exit 0
}
Write-Host 'FAIL: at least one test failed; inspect HTTP responses above.' -ForegroundColor Red
exit 1
