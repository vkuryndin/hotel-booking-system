param(
    [int]$RoomId = 2,
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$token = (Read-Host 'Paste adminToken (without Bearer)').Trim()
if ($token -match '^Bearer\s+(.+)$') { $token = $Matches[1] }
if ([string]::IsNullOrWhiteSpace($token)) { throw 'Token is empty.' }

# Pick new future dates on each run to reduce collisions with earlier tests.
$checkIn = (Get-Date).Date.AddDays((Get-Random -Minimum 180 -Maximum 550))
$checkOut = $checkIn.AddDays(3)
$key = [guid]::NewGuid().ToString()
$url = $BaseUrl.TrimEnd('/') + '/api/bookings'
$json = @{
    roomId = $RoomId
    checkIn = $checkIn.ToString('yyyy-MM-dd')
    checkOut = $checkOut.ToString('yyyy-MM-dd')
} | ConvertTo-Json -Compress

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(90)
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
$client.DefaultRequestHeaders.Add('Idempotency-Key', $key)

function Get-Result($response, $label) {
    $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $data = $null
    try { $data = $raw | ConvertFrom-Json } catch { }
    $http = [int]$response.StatusCode
    $bookingId = if ($null -ne $data) { $data.id } else { $null }
    $bookingStatus = if ($null -ne $data) { $data.status } else { $null }
    $code = if ($null -ne $data) { $data.code } else { $null }
    Write-Host ("{0}: HTTP={1}, bookingId={2}, status={3}, error={4}" -f $label, $http, $bookingId, $bookingStatus, $code)
    if ($http -ne 201) {
        $message = if ($null -ne $data) { $data.message } else { $raw }
        if ($message) { Write-Host ('  Detail: ' + $message) }
    }
    $response.Dispose()
    return [pscustomobject]@{ Http = $http; Id = $bookingId; Status = $bookingStatus; Code = $code }
}

$content1 = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
$content2 = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')

try {
    Write-Host ('URL: ' + $url)
    Write-Host ('Room: ' + $RoomId + '; dates: ' + $checkIn.ToString('yyyy-MM-dd') + ' - ' + $checkOut.ToString('yyyy-MM-dd'))
    Write-Host ('Idempotency-Key: ' + $key)

    # Launch both POST requests before waiting for either response.
    $task1 = $client.PostAsync($url, $content1)
    $task2 = $client.PostAsync($url, $content2)
    [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]@($task1, $task2))

    $r1 = Get-Result ($task1.Result) 'Request 1'
    $r2 = Get-Result ($task2.Result) 'Request 2'

    # After both finish, repeat with the same key and payload.
    $content3 = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
    try {
        $response3 = $client.PostAsync($url, $content3).GetAwaiter().GetResult()
        $r3 = Get-Result $response3 'Replay'
    }
    finally { $content3.Dispose() }

    $results = @($r1, $r2)
    $successes = @($results | Where-Object { $_.Http -eq 201 -and $_.Status -eq 'CONFIRMED' })
    $processing = @($results | Where-Object { $_.Http -eq 409 -and $_.Code -eq 'REQUEST_IN_PROGRESS' })

    $validParallel = (
        ($successes.Count -eq 1 -and $processing.Count -eq 1) -or
        ($successes.Count -eq 2 -and [string]$successes[0].Id -eq [string]$successes[1].Id)
    )
    $validReplay = (
        $r3.Http -eq 201 -and $r3.Status -eq 'CONFIRMED' -and
        $successes.Count -gt 0 -and
        [string]$r3.Id -eq [string]$successes[0].Id
    )

    if ($validParallel -and $validReplay) {
        Write-Host 'PASS: no duplicate booking; parallel response and replay are correct.'
        exit 0
    }
    Write-Host 'FAIL: results do not match the expected idempotency behavior.'
    exit 1
}
finally {
    $content1.Dispose()
    $content2.Dispose()
    $client.Dispose()
}
