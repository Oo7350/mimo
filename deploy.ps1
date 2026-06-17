$root = Split-Path -Parent $MyInvocation.MyCommand.Path

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
scp "$root\backend\target\mimo-backend-1.0.0-SNAPSHOT.jar" root@8.137.189.117:/root/Mimo/backend/target/
scp -r "$root\frontend\dist\*" root@8.137.189.117:/root/Mimo/frontend/dist/

Write-Host "=== 4/4 Restart Server ===" -ForegroundColor Cyan
ssh root@8.137.189.117 "chmod -R 755 /root/Mimo/frontend/dist && chmod 755 /root /root/Mimo /root/Mimo/frontend && systemctl restart mimo-backend && echo 'OK'"

Write-Host "=== Done ===`nhttp://8.137.189.117" -ForegroundColor Green
