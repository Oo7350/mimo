$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "=== 1/4 Build Backend ===" -ForegroundColor Cyan
Set-Location "$root\backend"
$env:JAVA_HOME = "D:\Java\jdk-21"
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "Backend build failed" }

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
