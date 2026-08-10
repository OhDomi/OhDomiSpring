# OhDomi 로컬 개발 환경 최초 설치 스크립트 (2026-08-10)
#
# 뭘 하는지: 3개 저장소(OhDomiReact/OhDomiSpring/closure-risk-model)를 클론하고,
# 각자 필요한 의존성을 설치하고, 로컬 MySQL(Docker)까지 띄워서 팀원이 git pull만 받으면
# 바로 개발을 시작할 수 있는 상태로 만든다. 한 번만 실행하면 됨 — 그 이후 매번 서버 켤
# 때는 start-local.ps1 사용.
#
# 자동화 안 되는 것(직접 준비 필요):
#   1) Docker Desktop, Node.js, JDK 17, Python 3.11+ 자체 설치 — 이 스크립트는 있는지
#      확인만 하고, 없으면 설치 링크를 안내한 뒤 중단한다.
#   2) closure-risk-model의 모델/데이터 번들(models/, data/) — 용량이 커서 git에 없음,
#      팀 채널에서 zip으로 별도 전달받아야 함.
#   3) closure-risk-model의 .env(API 키) — 팀 채널에서 값 전달받아야 함.
#
# 사용법: PowerShell을 관리자 권한 아닌 일반 권한으로 열고
#   powershell -ExecutionPolicy Bypass -File setup-local.ps1
#   (또는 setup-local.ps1을 더블클릭)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  ! $msg" -ForegroundColor Yellow }
function Write-Err($msg) { Write-Host "  ✗ $msg" -ForegroundColor Red }

$BaseDir = if ($env:OHDOMI_PROJECTS_DIR) { $env:OHDOMI_PROJECTS_DIR } else { "$HOME\projects" }
$Repos = @{
    'OhDomiReact'         = 'https://github.com/OhDomi/OhDomiReact.git'
    'OhDomiSpring'        = 'https://github.com/OhDomi/OhDomiSpring.git'
    'closure-risk-model'  = 'https://github.com/OhDomi/closure-risk-model.git'
}

# ---------- 1. 필수 도구 확인 ----------
Write-Step "필수 도구 확인"
$missing = @()
function Check-Tool($name, $cmd, $installUrl) {
    if (Get-Command $cmd -ErrorAction SilentlyContinue) {
        Write-Ok "$name 설치됨"
    } else {
        Write-Err "$name 없음 — $installUrl 에서 설치 후 다시 실행하세요"
        $script:missing += $name
    }
}
Check-Tool 'Git' 'git' 'https://git-scm.com/download/win'
Check-Tool 'Node.js/npm' 'npm' 'https://nodejs.org'
Check-Tool 'Java(JDK 17+)' 'java' 'https://adoptium.net'
Check-Tool 'Docker' 'docker' 'https://www.docker.com/products/docker-desktop'
Check-Tool 'Python' 'python' 'https://www.python.org/downloads'

if ($missing.Count -gt 0) {
    Write-Err "`n필수 도구가 빠져있어 설치를 진행할 수 없습니다: $($missing -join ', ')"
    Write-Host "위 도구들을 설치한 뒤 이 스크립트를 다시 실행하세요."
    exit 1
}

# ---------- 2. 저장소 클론/갱신 ----------
Write-Step "저장소 클론/갱신 (대상 폴더: $BaseDir)"
New-Item -ItemType Directory -Force -Path $BaseDir | Out-Null
foreach ($name in $Repos.Keys) {
    $path = Join-Path $BaseDir $name
    if (Test-Path $path) {
        Write-Ok "$name 이미 있음 — git pull"
        Push-Location $path
        git pull
        Pop-Location
    } else {
        Write-Ok "$name 클론 중..."
        git clone $Repos[$name] $path
    }
}

# ---------- 3. Docker MySQL ----------
Write-Step "Docker MySQL(ohdomi-mysql) 준비"
try {
    docker ps > $null 2>&1
} catch {
    Write-Warn "Docker Desktop이 꺼져있어 실행을 시도합니다..."
    $dockerExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerExe) {
        Start-Process $dockerExe
        Write-Host "  Docker Desktop 준비될 때까지 대기 중..."
        $ready = $false
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 3
            docker ps > $null 2>&1
            if ($?) { $ready = $true; break }
        }
        if (-not $ready) {
            Write-Err "Docker가 3분 안에 준비되지 않았습니다 — 수동으로 Docker Desktop을 켠 뒤 이 스크립트를 다시 실행하세요."
            exit 1
        }
    } else {
        Write-Err "Docker Desktop을 찾을 수 없습니다 — 수동으로 실행 후 다시 시도하세요."
        exit 1
    }
}
Write-Ok "Docker 준비됨"

$existing = docker ps -a --filter "name=^ohdomi-mysql$" --format "{{.Names}}"
if ($existing -eq 'ohdomi-mysql') {
    Write-Ok "ohdomi-mysql 컨테이너 이미 있음 — 기동"
    docker start ohdomi-mysql | Out-Null
} else {
    Write-Ok "ohdomi-mysql 컨테이너 새로 생성"
    docker run --name ohdomi-mysql `
        -e MYSQL_ALLOW_EMPTY_PASSWORD=yes `
        -e MYSQL_DATABASE=ohdomi `
        -p 3306:3306 `
        -v ohdomi-mysql-data:/var/lib/mysql `
        -d mysql:8.0 | Out-Null
}

Write-Host "  MySQL 준비될 때까지 대기 중..."
$mysqlReady = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    docker exec ohdomi-mysql mysqladmin ping -uroot --silent > $null 2>&1
    if ($?) { $mysqlReady = $true; break }
}
if ($mysqlReady) { Write-Ok "MySQL 준비 완료" } else { Write-Warn "MySQL이 아직 준비 안 됨 — 잠시 후 start-local.ps1에서 다시 확인됩니다" }

# ---------- 4. OhDomiReact 의존성 ----------
Write-Step "OhDomiReact 의존성 설치 (npm install)"
Push-Location (Join-Path $BaseDir 'OhDomiReact')
if (-not (Test-Path '.env') -and (Test-Path '.env.example')) {
    Copy-Item '.env.example' '.env'
    Write-Ok ".env 생성(.env.example에서 복사 — 로컬 개발은 빈 값이면 Vite 프록시를 씀, 수정 불필요)"
}
npm install
Pop-Location
Write-Ok "OhDomiReact 준비 완료"

# ---------- 5. OhDomiSpring 의존성 (Gradle wrapper가 첫 빌드 때 자동 다운로드) ----------
Write-Step "OhDomiSpring 빌드 캐시 준비 (Gradle)"
Push-Location (Join-Path $BaseDir 'OhDomiSpring')
.\gradlew.bat -q help
Pop-Location
Write-Ok "OhDomiSpring 준비 완료"

# ---------- 6. closure-risk-model 파이썬 환경 ----------
Write-Step "closure-risk-model 파이썬 가상환경 준비"
Push-Location (Join-Path $BaseDir 'closure-risk-model')
if (-not (Test-Path '.venv')) {
    python -m venv .venv
}
& '.\.venv\Scripts\pip.exe' install -q -r requirements.txt
if (-not (Test-Path '.env') -and (Test-Path '.env.example')) {
    Copy-Item '.env.example' '.env'
    Write-Warn ".env 생성됨 — 팀 채널에서 API 키 값을 받아 채워 넣어야 정상 동작합니다"
}
Pop-Location
Write-Ok "closure-risk-model 파이썬 패키지 설치 완료"

# ---------- 마무리 안내 ----------
Write-Step "설치 완료 — 남은 수동 작업"
Write-Host @"

  1) closure-risk-model/.env 에 API 키 채우기 (팀 채널에서 전달받은 값)
  2) closure-risk-model/models/, closure-risk-model/data/ 폴더에 모델·데이터 번들 압축 풀어넣기
     (팀 채널에서 zip으로 전달받음 — EC2 배포 때 쓰던 것과 같은 번들)

  다 됐으면 매번 서버 켤 때는:
    start-local.ps1

  경로: $BaseDir
"@ -ForegroundColor White
