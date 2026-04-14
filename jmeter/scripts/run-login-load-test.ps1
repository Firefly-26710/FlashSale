param(
    [string]$JMeterBin = "D:\tools\jmeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin",
    [string]$BaseUrl = "localhost",
    [int]$TargetPort = 8085,
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
$testPlan = Join-Path $jmeterDir 'plans\login-load.jmx'
$props = Join-Path $jmeterDir 'config\user.properties'
$outputDir = Join-Path $jmeterDir 'output'
$result = Join-Path $outputDir 'login-results.jtl'
$log = Join-Path $outputDir 'login-jmeter.log'

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
$successCount = ($data | Where-Object { $_.success -eq 'true' }).Count

$upstreamCounts = @{}
foreach ($row in $data) {
    $addr = [string]$row.upstream_addr
    if ([string]::IsNullOrWhiteSpace($addr) -or $addr -eq 'NOT_FOUND') {
        continue
    }
    if ($upstreamCounts.ContainsKey($addr)) {
        $upstreamCounts[$addr] += 1
    } else {
        $upstreamCounts[$addr] = 1
    }
}

Write-Host "=== Load Test Summary ==="
Write-Host ("Total Requests: {0}" -f $count)
Write-Host ("HTTP Success(true): {0}" -f $successCount)
Write-Host ("Average Response Time(ms): {0}" -f $avg)
Write-Host ("P95 Response Time(ms): {0}" -f $p95)

Write-Host ""
Write-Host "=== Backend Distribution (from X-Upstream-Addr) ==="
if ($upstreamCounts.Count -eq 0) {
    Write-Host 'No upstream header found in result rows.'
} else {
    $pairs = $upstreamCounts.GetEnumerator() | Sort-Object Name
    foreach ($pair in $pairs) {
        Write-Host ("{0} => {1}" -f $pair.Key, $pair.Value)
    }

    if ($upstreamCounts.Count -ge 2) {
        $vals = $upstreamCounts.Values | Sort-Object
        $min = [int]$vals[0]
        $max = [int]$vals[$vals.Count - 1]
        $deltaPct = if ($max -eq 0) { 0 } else { [math]::Round((($max - $min) * 100.0 / $max), 2) }
        Write-Host ("Difference Ratio(max-min)/max: {0}%" -f $deltaPct)
    }
}

Write-Host ""
Write-Host ("Result file: {0}" -f $result)
Write-Host ("JMeter log:  {0}" -f $log)
