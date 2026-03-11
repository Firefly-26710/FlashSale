param(
    [string]$JMeterBin = "D:\tools\jmeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin",
    [int]$Threads = 50,
    [int]$Loops = 20,
    [int]$RampUp = 10
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
$testPlan = Join-Path $jmeterDir 'plans\static-load.jmx'
$props = Join-Path $jmeterDir 'config\user.properties'
$outputDir = Join-Path $jmeterDir 'output'
$result = Join-Path $outputDir 'static-results.jtl'
$log = Join-Path $outputDir 'static-jmeter.log'

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}
if (Test-Path $result) { Remove-Item $result -Force }
if (Test-Path $log) { Remove-Item $log -Force }

& $jmeterExe -n -t $testPlan -q $props -l $result -j $log "-Jthreads=$Threads" "-Jloops=$Loops" "-Jrampup=$RampUp"

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
$successCount = ($data | Where-Object { $_.success -eq 'true' }).Count

Write-Host "=== Static File Load Test Summary ==="
Write-Host ("Total Requests: {0}" -f $count)
Write-Host ("HTTP Success(true): {0}" -f $successCount)
Write-Host ("Average Response Time(ms): {0}" -f $avg)
Write-Host ("P95 Response Time(ms): {0}" -f $p95)
Write-Host ""
Write-Host ("Result file: {0}" -f $result)
Write-Host ("JMeter log:  {0}" -f $log)
