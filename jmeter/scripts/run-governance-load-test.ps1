param(
    [string]$JMeterBin = "D:\tools\jmeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin",
    [string]$BaseUrl = "localhost",
    [int]$TargetPort = 8080,
    [int]$Threads = 30,
    [int]$Loops = 20,
    [int]$RampUp = 1
)

$ErrorActionPreference = 'Stop'

if (Test-Path $JMeterBin -PathType Container) {
    $jmeterExe = Join-Path $JMeterBin 'jmeter.bat'
} else {
    $jmeterExe = $JMeterBin
}

if (-not (Test-Path $jmeterExe)) {
    throw "JMeter executable not found: $jmeterExe"
}

$jmeterDir = Resolve-Path (Join-Path $PSScriptRoot '..')
$testPlan = Join-Path $jmeterDir 'plans\governance-load.jmx'
$props = Join-Path $jmeterDir 'config\user.properties'
$outputDir = Join-Path $jmeterDir 'output'
$result = Join-Path $outputDir 'governance-results.jtl'
$log = Join-Path $outputDir 'governance-jmeter.log'

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}
if (Test-Path $result) { Remove-Item $result -Force }
if (Test-Path $log) { Remove-Item $log -Force }

& $jmeterExe -n -t $testPlan -q $props -l $result -j $log "-Jthreads=$Threads" "-Jloops=$Loops" "-Jrampup=$RampUp" "-Jbase_url=$BaseUrl" "-Jtarget_port=$TargetPort"

if ($LASTEXITCODE -ne 0) {
    throw "JMeter run failed with exit code $LASTEXITCODE"
}

$data = Import-Csv $result
if (-not $data -or $data.Count -eq 0) {
    throw 'No samples found in JMeter result file'
}

$elapsed = $data | ForEach-Object { [double]$_.elapsed } | Sort-Object
$count = $elapsed.Count
$avg = [math]::Round((($elapsed | Measure-Object -Average).Average), 2)
$p95Index = [math]::Ceiling($count * 0.95) - 1
if ($p95Index -lt 0) { $p95Index = 0 }
$p95 = [math]::Round($elapsed[$p95Index], 2)

$statusCounts = $data | Group-Object responseCode | Sort-Object Name
$successCount = ($data | Where-Object { $_.success -eq 'true' }).Count
$rateLimitedCount = ($data | Where-Object { $_.responseCode -eq '429' }).Count
$rateLimitedRatio = if ($count -eq 0) { 0 } else { [math]::Round(($rateLimitedCount * 100.0 / $count), 2) }

Write-Host "=== Governance Load Test Summary ==="
Write-Host ("Total Requests: {0}" -f $count)
Write-Host ("HTTP Success(true): {0}" -f $successCount)
Write-Host ("Average Response Time(ms): {0}" -f $avg)
Write-Host ("P95 Response Time(ms): {0}" -f $p95)
Write-Host ("429 Count: {0}" -f $rateLimitedCount)
Write-Host ("429 Ratio(%): {0}" -f $rateLimitedRatio)

Write-Host ""
Write-Host "=== Response Code Distribution ==="
foreach ($group in $statusCounts) {
    Write-Host ("{0} => {1}" -f $group.Name, $group.Count)
}

Write-Host ""
Write-Host ("Result file: {0}" -f $result)
Write-Host ("JMeter log:  {0}" -f $log)
