# ---------------------------------------------------------------------------
#  로컬 서버 기동.  사용법:  .\run.ps1
#
#  접속 정보는 application.yml 에 직접 들어 있다 (사용자 결정 2026-07-14).
#  .env 도, 환경변수도, JDK 경로 설정도 필요 없다 — clone 직후 이것만 치면 뜬다.
#  (mvnw17.cmd 가 JDK 17+ 를 자동 탐색한다. JAVA_HOME 이 Java 8 인 PC 에서도 안전하다)
#
#  기동 후: http://localhost:8089
# ---------------------------------------------------------------------------
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "[run] 기동 : http://localhost:8089  (Swagger: /swagger-ui.html)"
Write-Host ""

# 'local' 프로파일을 활성화한다. application-local.yml 이 있으면 그 값(실제 AWS 키 등)이 로드되고,
# 없으면(예: 새로 clone) 조용히 무시되어 application.yml 기본값으로 동작한다.
& ".\mvnw17.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
