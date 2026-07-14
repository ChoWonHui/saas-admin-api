@REM ---------------------------------------------------------------------------
@REM  Maven wrapper pinned to JDK 17.
@REM
@REM  This repo is Java 17 (Spring Boot 3.2), but the machine-wide JAVA_HOME is
@REM  owned by the main project (Java 8 / Zulu 8) and must not be changed.
@REM  So call this script instead of mvnw: it overrides JAVA_HOME for this
@REM  process only, then delegates. The system environment stays untouched,
@REM  so the Java 8 build keeps working.
@REM
@REM  Usage:  .\mvnw17.cmd spring-boot:run
@REM          .\mvnw17.cmd liquibase:update
@REM
@REM  Override the JDK 17 location with the JAVA17_HOME environment variable.
@REM  (ASCII only on purpose -- cmd.exe mangles UTF-8 in .cmd files.)
@REM ---------------------------------------------------------------------------
@echo off
setlocal

@REM  Probe the known JDK 17 locations in order. JAVA17_HOME, if already set,
@REM  always wins -- the probe only runs when it is not defined.
if not defined JAVA17_HOME (
  for %%D in ("C:\SHIS\jdk-17" "C:\Program Files\Java\jdk-17") do (
    if not defined JAVA17_HOME if exist "%%~D\bin\java.exe" set "JAVA17_HOME=%%~D"
  )
)
if not defined JAVA17_HOME set "JAVA17_HOME=C:\SHIS\jdk-17"

if not exist "%JAVA17_HOME%\bin\java.exe" (
  echo [mvnw17] JDK 17 not found at: %JAVA17_HOME%
  echo [mvnw17] Install JDK 17, or set JAVA17_HOME to its location.
  exit /b 1
)

set "JAVA_HOME=%JAVA17_HOME%"
call "%~dp0mvnw.cmd" %*
exit /b %ERRORLEVEL%
