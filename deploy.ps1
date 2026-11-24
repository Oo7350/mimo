param(
    # 加 -Force 跳过确认（CI / 无人值守时用），正常手跑必须敲 y
    [switch]$Force
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

#region 部署确认（KNOWN-016）
# 以前这个脚本一执行就直接杀本地 java、编译、scp 覆盖生产、重启服务，
# 没有任何确认步骤 —— 手滑一次就是一次线上发布。现在先把"要动哪里"讲清楚再问。
$targetHost = "root@8.137.189.117"
Write-Host ""
Write-Host "即将部署到 $targetHost" -ForegroundColor Yellow
Write-Host "  1. 杀掉本机占用 backend jar 的 java 进程（如果有）" -ForegroundColor Gray
Write-Host "  2. mvn clean package -DskipTests" -ForegroundColor Gray
Write-Host "  3. npm run build" -ForegroundColor Gray
Write-Host "  4. scp 覆盖 $($targetHost):/root/Mimo/{backend/target,frontend/dist}" -ForegroundColor Gray
Write-Host "  5. ssh 重启 systemctl 服务 mimo-backend" -ForegroundColor Gray
Write-Host ""

if (-not $Force) {
    $answer = Read-Host "确认部署到生产环境? 输入 y 继续，其它任意键取消"
    if ($answer -ne "y" -and $answer -ne "Y") {
        Write-Host "已取消，什么都没改。" -ForegroundColor Green
        exit 1
    }
} else {
    Write-Host "-Force: 跳过确认" -ForegroundColor DarkYellow
}
#endregion 部署确认（KNOWN-016）

#region debug-point deploy-jar-file-locked
# 部署前清理：杀掉占用 backend target jar 的本地 java 进程，避免 mvn clean 失败
Write-Host "=== 0/4 Kill local Java holding target jar ===" -ForegroundColor Yellow
$holdingPids = Get-WmiObject Win32_Process |
    Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'mimo-backend' } |
    Select-Object -ExpandProperty ProcessId
if ($holdingPids) {
    Write-Host "  Killing local PIDs: $($holdingPids -join ', ')" -ForegroundColor Yellow
    $holdingPids | ForEach-Object {
        try { Stop-Process -Id $_ -Force -ErrorAction Stop } catch {}
    }
    Start-Sleep -Seconds 2
    $stillAlive = $holdingPids | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }
    if ($stillAlive) {
        Write-Host "  [WARN] PIDs still alive: $($stillAlive -join ', ') - waiting 5s" -ForegroundColor DarkYellow
        Start-Sleep -Seconds 5
    }
} else {
    Write-Host "  No local mimo backend running." -ForegroundColor DarkGray
}
#endregion debug-point deploy-jar-file-locked

Write-Host "=== 1/4 Build Backend ===" -ForegroundColor Cyan
Set-Location "$root\backend"
$env:JAVA_HOME = "D:\Java\jdk-21"

#region debug-point deploy-jar-file-locked
# 重试 mvn clean package 最多 3 次（防 Defender / 文件系统短暂卡顿）
$buildOk = $false
for ($i = 1; $i -le 3; $i++) {
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -eq 0) { $buildOk = $true; break }
    Write-Host "  [RETRY] mvn clean package failed (attempt $i/3)" -ForegroundColor DarkYellow
    Start-Sleep -Seconds 3
}
if (-not $buildOk) { throw "Backend build failed after 3 retries" }
#endregion debug-point deploy-jar-file-locked

Write-Host "=== 2/4 Build Frontend ===" -ForegroundColor Cyan
Set-Location "$root\frontend"
npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }

Write-Host "=== 3/4 Upload to Server ===" -ForegroundColor Cyan
scp "$root\backend\target\mimo-backend-1.0.0-SNAPSHOT.jar" "$($targetHost):/root/Mimo/backend/target/"
if ($LASTEXITCODE -ne 0) { throw "scp backend jar failed" }
scp -r "$root\frontend\dist\*" "$($targetHost):/root/Mimo/frontend/dist/"
if ($LASTEXITCODE -ne 0) { throw "scp frontend dist failed" }

Write-Host "=== 4/4 Restart Server ===" -ForegroundColor Cyan
ssh $targetHost "chmod -R 755 /root/Mimo/frontend/dist && chmod 755 /root /root/Mimo /root/Mimo/frontend && systemctl restart mimo-backend && echo 'OK'"
if ($LASTEXITCODE -ne 0) { throw "远端重启失败，请登服务器看 systemctl status mimo-backend" }

Write-Host "=== Done ===`nhttp://8.137.189.117" -ForegroundColor Green
