# OhDomi 로컬 개발 서버 실행 스크립트 (2026-08-10)
#
# setup-local.ps1을 먼저 한 번 실행해둔 상태에서, 매번 개발 시작할 때 이걸 실행하면
# Docker MySQL + Spring 백엔드(8080) + closure-risk-model API(8050) + React(5173)가
# 각각 새 창으로 뜬다. 창을 닫으면 그 서버만 꺼짐.

$ErrorActionPreference = 'Stop'
function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  ! $msg" -ForegroundColor Yellow }

$BaseDir = if ($env:OHDOMI_PROJECTS_DIR) { $env:OHDOMI_PROJECTS_DIR } else { "$HOME\projects" }

# ---------- 1. Docker MySQL ----------
Write-Step "Docker MySQL 확인"
try { docker ps > $null 2>&1 } catch {
    Write-Warn "Docker Desktop이 꺼져있어 실행합니다..."
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    for ($i = 0; $i -lt 60; $i++) { Start-Sleep 3; docker ps > $null 2>&1; if ($?) { break } }
}
$status = docker inspect -f '{{.State.Running}}' ohdomi-mysql 2>$null
if ($status -ne 'true') {
    docker start ohdomi-mysql | Out-Null
    Write-Ok "ohdomi-mysql 컨테이너 기동"
} else {
    Write-Ok "ohdomi-mysql 이미 실행 중"
}
for ($i = 0; $i -lt 30; $i++) {
    docker exec ohdomi-mysql mysqladmin ping -uroot --silent > $null 2>&1
    if ($?) { break }
    Start-Sleep 2
}
Write-Ok "MySQL 준비됨"

# ---------- 2. Spring 백엔드 (새 창) ----------
Write-Step "Spring 백엔드(8080) 새 창에서 시작"
$springDir = Join-Path $BaseDir 'OhDomiSpring'
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$springDir'; .\gradlew.bat bootRun"
Write-Ok "실행 요청함 — 창에서 'Started BackendApplication' 뜰 때까지 기다려주세요(수십 초)"

Write-Step "Spring 기동 대기 중 (최대 90초)"
$springReady = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep 2
    try {
        Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/ui/admin/stores' -UseBasicParsing -TimeoutSec 2 > $null
        $springReady = $true
        break
    } catch { }
}
if ($springReady) { Write-Ok "Spring 준비됨" } else { Write-Warn "Spring이 아직 응답 안 함 — 새 창 로그를 확인하세요" }

# ---------- 3. 김가네 216개 시드 데이터 (없으면 최초 1회만 자동 적용) ----------
if ($springReady) {
    Write-Step "김가네 216개 매장 시드 데이터 확인"
    $storeCount = docker exec ohdomi-mysql mysql -uroot -N -e "SELECT COUNT(*) FROM ohdomi.stores WHERE store_code LIKE 'KG-%';" 2>$null
    if ($storeCount -match '^\s*0\s*$') {
        Write-Ok "매장 216개 임포트 중..."
        Get-Content (Join-Path $springDir 'kimgane_216_stores_import.sql') -Raw |
            docker exec -i ohdomi-mysql mysql -uroot --default-character-set=utf8mb4 ohdomi
    } else {
        Write-Ok "매장 데이터 이미 있음 (건너뜀)"
    }

    $riskCount = docker exec ohdomi-mysql mysql -uroot -N -e "SELECT COUNT(*) FROM ohdomi.risk_assessments WHERE store_id BETWEEN 1000 AND 1215;" 2>$null
    if ($riskCount -match '^\s*0\s*$') {
        Write-Ok "리스크 평가 216건 임포트 중..."
        Get-Content (Join-Path $springDir 'kimgane_216_risk_assessments_import.sql') -Raw |
            docker exec -i ohdomi-mysql mysql -uroot --default-character-set=utf8mb4 ohdomi
    } else {
        Write-Ok "리스크 평가 데이터 이미 있음 (건너뜀)"
    }
}

# ---------- 4. closure-risk-model API (새 창) ----------
Write-Step "closure-risk-model API(8050) 새 창에서 시작"
$modelDir = Join-Path $BaseDir 'closure-risk-model'
if (Test-Path (Join-Path $modelDir 'models')) {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$modelDir'; .\.venv\Scripts\python.exe -m uvicorn src.api:app --port 8050"
    Write-Ok "실행 요청함"
} else {
    Write-Warn "models/ 폴더가 없어 건너뜀 — 팀 채널에서 모델·데이터 번들을 받아 넣은 뒤 수동으로 실행하세요:"
    Write-Host "    cd '$modelDir'; .\.venv\Scripts\python.exe -m uvicorn src.api:app --port 8050"
}

# ---------- 5. React (새 창) ----------
Write-Step "React 개발 서버(5173) 새 창에서 시작"
$reactDir = Join-Path $BaseDir 'OhDomiReact'
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$reactDir'; npm run dev"
Write-Ok "실행 요청함"

Write-Step "완료"
Write-Host @"

  브라우저에서 열기: http://localhost:5173

  각 서버는 새로 뜬 PowerShell 창에서 계속 돌아갑니다 — 끄려면 그 창을 닫으세요.
"@ -ForegroundColor White
