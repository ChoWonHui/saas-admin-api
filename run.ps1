# ---------------------------------------------------------------------------
#  로컬 서버 기동.  사용법:  .\run.ps1
#
#  접속 정보는 application.yml 에 직접 들어 있다 (사용자 결정 2026-07-14).
#  .env 도, 환경변수도 없어도 그냥 뜬다. 이 스크립트가 하는 일은 JDK 17 로 띄우는 것뿐이다.
#  (시스템 JAVA_HOME 이 Java 8 인 PC 도 있어서 mvnw 를 직접 부르면 안 된다 → mvnw17.cmd)
#
#  기동 후: http://localhost:8089
# ---------------------------------------------------------------------------
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "[run] 기동 : http://localhost:8089  (Swagger: /swagger-ui.html)"
Write-Host ""

& ".\mvnw17.cmd" spring-boot:run
