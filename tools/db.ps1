# ---------------------------------------------------------------------------
#  DB 접속 도구.  MySQL CLI 가 이 PC 에 없어서 만든 것이다.
#
#  사용법:
#    .\tools\db.ps1 "SHOW TABLES"
#    .\tools\db.ps1 "SELECT * FROM tenant"
#    .\tools\db.ps1 -File tools\sql\some.sql
#    .\tools\db.ps1 -Admin "GRANT ... TO 'saas_app'@'%'"      # 관리자 계정으로
#
#  기본 계정은 .env 의 saas_app (tenant_saas 에 대해 DDL 포함 전 권한).
#  -Admin 은 계정 생성/권한 부여처럼 스키마 밖 작업이 필요할 때만 쓴다.
#  (그 경우 SAAS_ROOT_USER / SAAS_ROOT_PASSWORD 를 .env 에 넣어둬야 한다)
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]] $Sql,

    [string] $File,
    [switch] $Admin
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- .env 로딩 (UTF-8 필수: 기본값 ANSI 로 읽으면 한글이 깨진다) ---
if (-not (Test-Path ".env")) { throw ".env 가 없다." }
$cfg = @{}
Get-Content ".env" -Encoding UTF8 | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

# --- JDBC 드라이버 확보 (최초 1회만 내려받는다) ---
$jar = Get-ChildItem "target\lib\mysql-connector-j-*.jar" -ErrorAction SilentlyContinue |
       Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) {
    Write-Host "[db] JDBC 드라이버를 내려받는다..." -ForegroundColor DarkGray
    & ".\mvnw17.cmd" -B -q dependency:copy-dependencies `
        "-DincludeArtifactIds=mysql-connector-j" "-DoutputDirectory=target/lib" | Out-Null
    $jar = Get-ChildItem "target\lib\mysql-connector-j-*.jar" |
           Select-Object -First 1 -ExpandProperty FullName
}

# --- 접속 정보 ---
if ($Admin) {
    if (-not $cfg['SAAS_ROOT_USER']) {
        throw "-Admin 을 쓰려면 .env 에 SAAS_ROOT_USER / SAAS_ROOT_PASSWORD 가 있어야 한다."
    }
    $env:DB_USER = $cfg['SAAS_ROOT_USER']
    $env:DB_PASS = $cfg['SAAS_ROOT_PASSWORD']
    Write-Host "[db] 관리자 계정으로 접속: $($env:DB_USER)" -ForegroundColor Yellow
} else {
    $env:DB_USER = $cfg['SAAS_DB_USER']
    $env:DB_PASS = $cfg['SAAS_DB_PASSWORD']
}
$env:DB_URL = $cfg['SAAS_DB_URL']

# --- 실행 ---
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$java = "C:\SHIS\jdk-17\bin\java.exe"
if ($cfg['JAVA17_HOME']) { $java = Join-Path $cfg['JAVA17_HOME'] "bin\java.exe" }

if ($File) {
    & $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp $jar "tools\Db.java" -f $File
} elseif ($Sql) {
    & $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp $jar "tools\Db.java" -e ($Sql -join ' ')
} else {
    Write-Host "사용법: .\tools\db.ps1 `"SELECT * FROM tenant`"   또는   -File path.sql"
}
