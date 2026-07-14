# ---------------------------------------------------------------------------
#  로컬 서버 기동.  사용법:  .\run.ps1
#
#  하는 일:
#    1) .env 를 UTF-8 로 읽어 환경변수로 주입한다.
#       (-Encoding UTF8 은 필수. PowerShell 5.1 의 Get-Content 기본값은 ANSI(CP949) 라
#        이걸 빼면 SAAS_ADMIN_NAME 같은 한글 값이 깨진 채로 들어간다.)
#    2) mvnw17.cmd 로 앱을 띄운다. (JDK 17 — 시스템 JAVA_HOME 은 Java 8 이라 mvnw 를 직접 쓰면 안 된다)
#
#  기동 후: http://localhost:8081
# ---------------------------------------------------------------------------
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Write-Host "[run] .env 가 없습니다. .env.example 을 복사해 값을 채우세요." -ForegroundColor Red
    exit 1
}

Get-Content ".env" -Encoding UTF8 | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}

Write-Host "[run] DB   : $env:SAAS_DB_USER @ tenant_saas"
Write-Host "[run] 기동 : http://localhost:8081"
Write-Host ""

& ".\mvnw17.cmd" spring-boot:run
