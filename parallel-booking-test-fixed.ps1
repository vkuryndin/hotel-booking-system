param(
    [int]$RoomId = 2,
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$StressCount = 10,
    [string]$StartDate = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

if ($StressCount -lt 2 -or $StressCount -gt 50) {
    throw 'StressCount must be between 2 and 50.'
}

$token = (Read-Host 'Paste adminToken (without Bearer)').Trim()
if ($token -match '^Bearer\s+(.+)$') { $token = $Matches[1] }
if ([string]::IsNullOrWhiteSpace($token)) { throw 'Token is empty.' }

# Random future dates keep repeated runs separate from earlier reservations.
if ([string]::IsNullOrWhiteSpace($StartDate)) {
    $baseDate = ([datetime]'2035-01-01').AddDays((Get-Random -Minimum 0 -Maximum 3000))
} else {
    $baseDate = [datetime]::ParseExact(
        $StartDate,
        'yyyy-MM-dd',
        [System.Globalization.CultureInfo]::InvariantCulture
    )
}

$url = $BaseUrl.TrimEnd('/') + '/api/bookings'
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(90)
$client.DefaultRequestHeaders.Authorization = `
    [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)

function New-BookingJson {
    param([datetime]$CheckIn)
    return (@{
        roomId = $RoomId
        checkIn = $CheckIn.ToString('yyyy-MM-dd')
        checkOut = $CheckIn.AddDays(3).ToString('yyyy-MM-dd')
    } | ConvertTo-Json -Compress)
}

function Read-BookingResponse {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [string]$Label
    )

    try {
        $raw = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = $null
        try { $data = $raw | ConvertFrom-Json } catch { }

        $http = [int]$Response.StatusCode
        $id = if ($null -ne $data) { $data.id } else { $null }
        $status = if ($null -ne $data) { $data.status } else { $null }
        $code = if ($null -ne $data) { $data.code } else { $null }
        Write-Host ("{0}: HTTP={1}, bookingId={2}, status={3}, error={4}" -f `
            $Label, $http, $id, $status, $code)
        if ($http -ne 201) {
            $detail = if ($null -ne $data -and $data.message) { $data.message } else { $raw }
            if ($detail) { Write-Host ('  Detail: ' + $detail) }
        }

        return [pscustomobject]@{
            Http = $http
            Id = $id
            Status = $status
            Code = $code
        }
    }
    finally {
        $Response.Dispose()
    }
}

function Invoke-BookingBatch {
    param(
        [string]$Json,
        [string[]]$Keys,
        [string]$LabelPrefix
    )

    $pending = @()
    try {
        # Start all requests before waiting for any response.
        for ($i = 0; $i -lt $Keys.Count; $i++) {
            $req = [System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::Post, $url
            )
            $req.Headers.Add('Idempotency-Key', $Keys[$i])
            $req.Content = [System.Net.Http.StringContent]::new(
                $Json, [System.Text.Encoding]::UTF8, 'application/json'
            )
            $pending += [pscustomobject]@{
                Request = $req
                Task = $client.SendAsync($req)
            }
        }

        $tasks = [System.Threading.Tasks.Task[]]@($pending | ForEach-Object { $_.Task })
        [System.Threading.Tasks.Task]::WaitAll($tasks)

        for ($i = 0; $i -lt $pending.Count; $i++) {
            Read-BookingResponse -Response $pending[$i].Task.Result `
                -Label ("{0} {1}" -f $LabelPrefix, ($i + 1))
        }
    }
    finally {
        foreach ($item in $pending) {
            $item.Request.Dispose()
        }
    }
}

$allPassed = $true
try {
    Write-Host ('URL: ' + $url)
    Write-Host ('Room: ' + $RoomId)

    # TEST 1: two parallel requests with ONE idempotency key, then replay.
    $json1 = New-BookingJson -CheckIn $baseDate
    $key1 = [guid]::NewGuid().ToString()
    Write-Host "`n### TEST 1 - SAME key, 2 parallel requests + replay"
    Write-Host ("Dates: {0} - {1}" -f $baseDate.ToString('yyyy-MM-dd'), $baseDate.AddDays(3).ToString('yyyy-MM-dd'))

    $first = @(Invoke-BookingBatch -Json $json1 -Keys @($key1, $key1) -LabelPrefix 'Parallel')
    $replay = @(Invoke-BookingBatch -Json $json1 -Keys @($key1) -LabelPrefix 'Replay')[0]

    $ok = @($first | Where-Object { $_.Http -eq 201 -and $_.Status -eq 'CONFIRMED' })
    $inProgress = @($first | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'REQUEST_IN_PROGRESS' })
    $parallelValid = (
        ($ok.Count -eq 2 -and [string]$ok[0].Id -eq [string]$ok[1].Id) -or
        ($ok.Count -eq 1 -and $inProgress.Count -eq 1)
    )
    $replayValid = (
        $ok.Count -gt 0 -and
        $replay.Http -eq 201 -and $replay.Status -eq 'CONFIRMED' -and
        [string]$replay.Id -eq [string]$ok[0].Id
    )
    $test1Passed = $parallelValid -and $replayValid -and $null -ne $replay.Id
    Write-Host $(if ($test1Passed) { 'PASS TEST 1: idempotency preserved.' } else { 'FAIL TEST 1: unexpected idempotency result.' })
    if (-not $test1Passed) { $allPassed = $false }

    # TEST 2: two concurrent attempts with DIFFERENT keys for the same room/dates.
    $date2 = $baseDate.AddDays(14)
    $json2 = New-BookingJson -CheckIn $date2
    $key2a = [guid]::NewGuid().ToString()
    $key2b = [guid]::NewGuid().ToString()
    Write-Host "`n### TEST 2 - DIFFERENT keys, 2 parallel requests"
    Write-Host ("Dates: {0} - {1}" -f $date2.ToString('yyyy-MM-dd'), $date2.AddDays(3).ToString('yyyy-MM-dd'))

    $second = @(Invoke-BookingBatch -Json $json2 -Keys @($key2a, $key2b) -LabelPrefix 'Parallel')
    $winner = @($second | Where-Object { $_.Http -eq 201 -and $_.Status -eq 'CONFIRMED' })
    $loser = @($second | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'ROOM_ALREADY_BOOKED' })
    $test2Passed = ($winner.Count -eq 1 -and $loser.Count -eq 1 -and $null -ne $winner[0].Id)
    Write-Host $(if ($test2Passed) { 'PASS TEST 2: exactly one reservation.' } else { 'FAIL TEST 2: possible concurrency issue.' })
    if (-not $test2Passed) { $allPassed = $false }

    # TEST 3: new key, already occupied date range, even after the race is over.
    Write-Host "`n### TEST 3 - NEW key for the occupied dates"
    $third = @(Invoke-BookingBatch -Json $json2 -Keys @([guid]::NewGuid().ToString()) -LabelPrefix 'Conflict')[0]
    $test3Passed = ($third.Http -eq 409 -and $third.Code -eq 'ROOM_ALREADY_BOOKED')
    Write-Host $(if ($test3Passed) { 'PASS TEST 3: occupied room rejected.' } else { 'FAIL TEST 3: occupied room was not rejected.' })
    if (-not $test3Passed) { $allPassed = $false }

    # TEST 4: several independent concurrent attempts for one room and date range.
    $date4 = $baseDate.AddDays(28)
    $json4 = New-BookingJson -CheckIn $date4
    $keys4 = @(for ($i = 0; $i -lt $StressCount; $i++) { [guid]::NewGuid().ToString() })
    Write-Host "`n### TEST 4 - STRESS: $StressCount DIFFERENT keys, parallel requests"
    Write-Host ("Dates: {0} - {1}" -f $date4.ToString('yyyy-MM-dd'), $date4.AddDays(3).ToString('yyyy-MM-dd'))

    $stress = @(Invoke-BookingBatch -Json $json4 -Keys $keys4 -LabelPrefix 'Stress')
    $stressWinners = @($stress | Where-Object { $_.Http -eq 201 -and $_.Status -eq 'CONFIRMED' -and $null -ne $_.Id })
    $stressConflicts = @($stress | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'ROOM_ALREADY_BOOKED' })
    $test4Passed = ($stress.Count -eq $StressCount -and $stressWinners.Count -eq 1 -and $stressConflicts.Count -eq ($StressCount - 1))
    Write-Host $(if ($test4Passed) { 'PASS TEST 4: exactly one reservation under concurrent load.' } else { 'FAIL TEST 4: unexpected concurrent results.' })
    if (-not $test4Passed) { $allPassed = $false }
}
catch {
    $allPassed = $false
    Write-Host ('FAIL: ' + $_.Exception.Message)
}
finally {
    $client.Dispose()
}

Write-Host "`n### OVERALL RESULT"
if ($allPassed) {
    Write-Host 'PASS: all four tests passed.'
    exit 0
}
Write-Host 'FAIL: one or more tests failed; inspect HTTP responses above.'
exit 1
