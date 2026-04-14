param(
    [string]$JMeterBin = "D:\tools\jmeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin",
    [string]$ComposeFile = "docker-compose.yml",
    [SecureString]$MysqlRootPassword,
    [string]$BaseUrl = "localhost",
    [int]$TargetPort = 8085,
    [int]$Threads = 500,
    [int]$Loops = 1,
    [int]$RampUp = 60,
    [int]$TargetStock = 300,
    [int]$VerificationTimeoutSec = 120,
    [int]$PrewarmRequests = 100,
    [SecureString]$TestPassword,
    [switch]$CleanupAfterRun
)

$ErrorActionPreference = 'Stop'

function ConvertTo-PlainText {
    param([Parameter(Mandatory = $true)][SecureString]$SecureText)

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureText)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$mysqlRootPasswordPlain = if ($null -ne $MysqlRootPassword) {
    ConvertTo-PlainText -SecureText $MysqlRootPassword
} elseif (-not [string]::IsNullOrWhiteSpace($env:MYSQL_ROOT_PASSWORD)) {
    $env:MYSQL_ROOT_PASSWORD
} else {
    'root123'
}

$testPasswordPlain = if ($null -ne $TestPassword) {
    ConvertTo-PlainText -SecureText $TestPassword
} elseif (-not [string]::IsNullOrWhiteSpace($env:JMETER_TEST_PASSWORD)) {
    $env:JMETER_TEST_PASSWORD
} else {
    'Passw0rd!23'
}

function Invoke-DockerComposeMySql {
    param(
        [Parameter(Mandatory = $true)][string]$Container,
        [Parameter(Mandatory = $true)][string]$Sql
    )

    $cmd = @('compose', '-f', $ComposeFile, 'exec', '-T', $Container, 'mysql', '-uroot', "-p$mysqlRootPasswordPlain", '-N', '-e', $Sql)
    $output = & docker @cmd
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed in container '$Container'."
    }
    return ($output | Out-String).Trim()
}

function Invoke-ApiJson {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $false)][string]$Body
    )

    try {
        if ([string]::IsNullOrWhiteSpace($Body)) {
            return Invoke-RestMethod -Method $Method -Uri $Url -TimeoutSec 15
        }
        return Invoke-RestMethod -Method $Method -Uri $Url -ContentType 'application/json' -Body $Body -TimeoutSec 15
    }
    catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            if ($statusCode -eq 400) {
                return $null
            }
        }
        throw
    }
}

function Get-IntValue {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 0
    }
    return [int]($Text.Trim())
}

function Get-ProductOrderCount {
    param(
        [Parameter(Mandatory = $true)][int]$ProductId,
        [Parameter(Mandatory = $true)][string]$Status
    )

    $sql = @"
SELECT SUM(cnt)
FROM (
    SELECT COUNT(*) AS cnt FROM FlashSale.orders_0 WHERE product_id = $ProductId AND status = '$Status'
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale.orders_1 WHERE product_id = $ProductId AND status = '$Status'
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale_ds1.orders_0 WHERE product_id = $ProductId AND status = '$Status'
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale_ds1.orders_1 WHERE product_id = $ProductId AND status = '$Status'
) t;
"@
    return Get-IntValue (Invoke-DockerComposeMySql -Container 'mysql-primary' -Sql $sql)
}

function Get-ProductDistinctUserCount {
    param([Parameter(Mandatory = $true)][int]$ProductId)

    $sql = @"
SELECT COUNT(DISTINCT user_id)
FROM (
    SELECT user_id FROM FlashSale.orders_0 WHERE product_id = $ProductId
    UNION ALL
    SELECT user_id FROM FlashSale.orders_1 WHERE product_id = $ProductId
    UNION ALL
    SELECT user_id FROM FlashSale_ds1.orders_0 WHERE product_id = $ProductId
    UNION ALL
    SELECT user_id FROM FlashSale_ds1.orders_1 WHERE product_id = $ProductId
) u;
"@
    return Get-IntValue (Invoke-DockerComposeMySql -Container 'mysql-primary' -Sql $sql)
}

function Get-ProductTotalOrderCount {
    param([Parameter(Mandatory = $true)][int]$ProductId)

    $sql = @"
SELECT SUM(cnt)
FROM (
    SELECT COUNT(*) AS cnt FROM FlashSale.orders_0 WHERE product_id = $ProductId
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale.orders_1 WHERE product_id = $ProductId
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale_ds1.orders_0 WHERE product_id = $ProductId
    UNION ALL
    SELECT COUNT(*) AS cnt FROM FlashSale_ds1.orders_1 WHERE product_id = $ProductId
) t;
"@
    return Get-IntValue (Invoke-DockerComposeMySql -Container 'mysql-primary' -Sql $sql)
}

if (Test-Path $JMeterBin -PathType Container) {
    $jmeterExe = Join-Path $JMeterBin 'jmeter.bat'
} else {
    $jmeterExe = $JMeterBin
}

if (-not (Test-Path $jmeterExe)) {
    throw "JMeter executable not found: $jmeterExe"
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $projectRoot

try {
    $jmeterDir = Resolve-Path (Join-Path $PSScriptRoot '..')
    $testPlan = Join-Path $jmeterDir 'plans\seckill-500-load.jmx'
    $props = Join-Path $jmeterDir 'config\user.properties'
    $outputDir = Join-Path $jmeterDir 'output'
    $result = Join-Path $outputDir 'seckill-500-results.jtl'
    $log = Join-Path $outputDir 'seckill-500-jmeter.log'

    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir | Out-Null
    }
    if (Test-Path $result) { Remove-Item $result -Force }
    if (Test-Path $log) { Remove-Item $log -Force }

    & docker compose -f $ComposeFile ps | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose unavailable or compose file invalid: $ComposeFile"
    }

    $runId = Get-Date -Format 'yyyyMMddHHmmss'
    $productName = "jmeter_seckill_${runId}"
    $tokenCsv = Join-Path $outputDir ("seckill-500-users-{0}.csv" -f $runId)

    Write-Host "Preparing test product and stock..."
    $prepareSql = @"
USE FlashSale;
INSERT INTO product (name, description, price, stock, image_url)
VALUES ('$productName', 'JMeter seckill verification product', 1.00, $TargetStock, 'https://picsum.photos/seed/jmeter-seckill/640/360');
SELECT id FROM product WHERE name = '$productName' ORDER BY id DESC LIMIT 1;
"@
    $productIdText = Invoke-DockerComposeMySql -Container 'mysql-inventory' -Sql $prepareSql
    $productId = Get-IntValue $productIdText
    if ($productId -le 0) {
        throw "Failed to create test product."
    }

    Write-Host ("Test product created: id={0}, name={1}, stock={2}" -f $productId, $productName, $TargetStock)

    Write-Host "Preparing 500 test users and JWT tokens..."
    "username,token" | Set-Content -Path $tokenCsv -Encoding UTF8

    $baseApi = "http://{0}:{1}" -f $BaseUrl, $TargetPort
    for ($i = 1; $i -le $Threads; $i++) {
        $username = "jmeter_sk_${runId}_$i"
        $bodyObj = @{ username = $username; password = $testPasswordPlain }
        $bodyJson = $bodyObj | ConvertTo-Json -Compress

        Invoke-ApiJson -Method 'POST' -Url "$baseApi/api/auth/register" -Body $bodyJson | Out-Null

        $token = $null
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            $loginResult = Invoke-ApiJson -Method 'POST' -Url "$baseApi/api/auth/login" -Body $bodyJson
            if ($null -ne $loginResult -and -not [string]::IsNullOrWhiteSpace([string]$loginResult.token)) {
                $token = [string]$loginResult.token
                break
            }
            Start-Sleep -Milliseconds 200
        }

        if ([string]::IsNullOrWhiteSpace($token)) {
            throw "Failed to login test user: $username"
        }

        Add-Content -Path $tokenCsv -Value ("{0},{1}" -f $username, $token) -Encoding UTF8
    }

    Write-Host ("Token CSV prepared: {0}" -f $tokenCsv)

    if ($PrewarmRequests -gt 0) {
        Write-Host ("Prewarming gateway/services with {0} product-list requests..." -f $PrewarmRequests)
        for ($k = 1; $k -le $PrewarmRequests; $k++) {
            try {
                Invoke-RestMethod -Method GET -Uri "$baseApi/api/products" -TimeoutSec 8 | Out-Null
            }
            catch {
                # 预热阶段忽略个别失败，避免影响正式压测。
            }
        }
    }

    & $jmeterExe -n -t $testPlan -q $props -l $result -j $log "-Jthreads=$Threads" "-Jloops=$Loops" "-Jrampup=$RampUp" "-Jbase_url=$BaseUrl" "-Jtarget_port=$TargetPort" "-Jproduct_id=$productId" "-Jusers_csv=$tokenCsv"

    if ($LASTEXITCODE -ne 0) {
        throw "JMeter run failed with exit code $LASTEXITCODE"
    }

    if (-not (Test-Path $result)) {
        throw "JMeter did not generate result file: $result. Please check log: $log"
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

    $seckillRows = $data | Where-Object { $_.label -eq 'POST /api/orders/seckill/{productId}' }
    $seckillTotal = $seckillRows.Count
    $accepted202 = ($seckillRows | Where-Object { $_.responseCode -eq '202' }).Count
    $badReq400 = ($seckillRows | Where-Object { $_.responseCode -eq '400' }).Count
    $responseCodeGroups = $seckillRows | Group-Object responseCode | Sort-Object Name

    $pendingPayment = 0
    $paid = 0
    $payFailed = 0
    $totalOrders = 0
    $stockLeft = $TargetStock
    $elapsedWait = 0

    Write-Host "Waiting async order pipeline to settle..."
    while ($elapsedWait -lt $VerificationTimeoutSec) {
        $pendingPayment = Get-ProductOrderCount -ProductId $productId -Status 'PENDING_PAYMENT'
        $paid = Get-ProductOrderCount -ProductId $productId -Status 'PAID'
        $payFailed = Get-ProductOrderCount -ProductId $productId -Status 'PAY_FAILED'

        $stockSql = "SELECT stock FROM FlashSale.product WHERE id = $productId LIMIT 1;"
        $stockLeft = Get-IntValue (Invoke-DockerComposeMySql -Container 'mysql-inventory' -Sql $stockSql)

        $totalOrders = Get-ProductTotalOrderCount -ProductId $productId

        if ($totalOrders -ge $TargetStock -or $stockLeft -eq 0) {
            break
        }

        Start-Sleep -Seconds 2
        $elapsedWait += 2
    }

    $activeOrders = $pendingPayment + $paid + $payFailed
    $totalOrders = Get-ProductTotalOrderCount -ProductId $productId
    $distinctUsers = Get-ProductDistinctUserCount -ProductId $productId

    Write-Host ""
    Write-Host "=== Seckill 500 Test Summary ==="
    Write-Host ("RunId: {0}" -f $runId)
    Write-Host ("ProductId: {0}" -f $productId)
    Write-Host ("Total Samples: {0}" -f $count)
    Write-Host ("Average Response Time(ms): {0}" -f $avg)
    Write-Host ("P95 Response Time(ms): {0}" -f $p95)
    Write-Host ""
    Write-Host "=== API Result Summary ==="
    Write-Host ("Seckill 202 Accepted: {0}/{1}" -f $accepted202, $seckillTotal)
    Write-Host ("Seckill 400: {0}" -f $badReq400)
    Write-Host "Seckill Response Codes:"
    foreach ($group in $responseCodeGroups) {
        Write-Host ("  {0}: {1}" -f $group.Name, $group.Count)
    }
    Write-Host ""
    Write-Host "=== Consistency Checks ==="
    Write-Host ("Target Stock: {0}" -f $TargetStock)
    Write-Host ("DB Stock Left: {0}" -f $stockLeft)
    Write-Host ("Total Orders(all status): {0}" -f $totalOrders)
    Write-Host ("Active Orders (PENDING_PAYMENT+PAID+PAY_FAILED): {0}" -f $activeOrders)
    Write-Host ("Distinct Order Users: {0}" -f $distinctUsers)

    $oversellOk = $totalOrders -le $TargetStock
    $stockMatchOk = ($stockLeft + $totalOrders) -eq $TargetStock
    $noDupUserOk = $distinctUsers -eq $totalOrders

    Write-Host ("No Oversell: {0}" -f $oversellOk)
    Write-Host ("Stock Conservation(stock + activeOrders == targetStock): {0}" -f $stockMatchOk)
    Write-Host ("No Duplicate User Orders: {0}" -f $noDupUserOk)

    if (-not $oversellOk -or -not $stockMatchOk -or -not $noDupUserOk) {
        Write-Warning 'One or more consistency checks failed. Please inspect logs and database state.'
    }

    Write-Host ""
    Write-Host ("Result file: {0}" -f $result)
    Write-Host ("JMeter log:  {0}" -f $log)

    if ($CleanupAfterRun) {
        Write-Host ""
        Write-Host "Cleaning up test data..."

        $cleanupSqlInventory = @"
USE FlashSale;
DELETE FROM product WHERE id = $productId;
DELETE FROM user WHERE username LIKE 'jmeter_sk_${runId}_%';
"@
        Invoke-DockerComposeMySql -Container 'mysql-inventory' -Sql $cleanupSqlInventory | Out-Null

        $cleanupSqlOrders = @"
DELETE FROM FlashSale.orders_0 WHERE product_id = $productId;
DELETE FROM FlashSale.orders_1 WHERE product_id = $productId;
DELETE FROM FlashSale_ds1.orders_0 WHERE product_id = $productId;
DELETE FROM FlashSale_ds1.orders_1 WHERE product_id = $productId;
"@
        Invoke-DockerComposeMySql -Container 'mysql-primary' -Sql $cleanupSqlOrders | Out-Null

        & docker compose -f $ComposeFile exec -T redis redis-cli DEL "seckill:stock:$productId" "seckill:users:$productId" | Out-Null

        Write-Host "Cleanup completed."
    }

    if (Test-Path $tokenCsv) {
        Remove-Item $tokenCsv -Force
    }
}
finally {
    Pop-Location
}
