@REM ---------------------------------------------------------------------------
@REM  Maven wrapper that finds a JDK 17+ by itself, then delegates to mvnw.
@REM
@REM  Goal: "git clone" on ANY Windows PC and it just runs, as long as a
@REM  JDK 17 or newer exists somewhere reasonable. Search order:
@REM
@REM    1. JAVA17_HOME environment variable (explicit override, always wins)
@REM    2. JAVA_HOME, if it points at a JDK 17 or newer
@REM    3. java.exe on PATH, if it is 17 or newer
@REM    4. Common install folders (Oracle, Temurin/Adoptium, Microsoft,
@REM       Amazon Corretto, Zulu, legacy C:\SHIS)
@REM
@REM  The JDK version is read from the JDK's "release" file (JAVA_VERSION=...),
@REM  so nothing is executed while probing. JAVA_HOME is overridden for this
@REM  process only -- the system environment stays untouched.
@REM
@REM  Usage:  .\mvnw17.cmd spring-boot:run
@REM  (ASCII only on purpose -- cmd.exe mangles UTF-8 in .cmd files.)
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set "JDK17="

@REM -- 1) explicit override ---------------------------------------------------
if defined JAVA17_HOME (
  if exist "%JAVA17_HOME%\bin\java.exe" (
    set "JDK17=%JAVA17_HOME%"
    goto :run
  )
  echo [mvnw17] JAVA17_HOME is set but java.exe is not there: "%JAVA17_HOME%"
  exit /b 1
)

@REM -- 2) JAVA_HOME, if already 17+ --------------------------------------------
if defined JAVA_HOME call :accept "%JAVA_HOME%"
if defined JDK17 goto :run

@REM -- 3) java.exe on PATH, if 17+ ---------------------------------------------
for /f "delims=" %%J in ('where java.exe 2^>nul') do (
  if not defined JDK17 call :accept "%%~dpJ.."
)
if defined JDK17 goto :run

@REM -- 4) common install locations ----------------------------------------------
for %%P in (
  "C:\Program Files\Java"
  "C:\Program Files\Eclipse Adoptium"
  "C:\Program Files\Microsoft"
  "C:\Program Files\Amazon Corretto"
  "C:\Program Files\Zulu"
  "C:\Program Files (x86)\Java"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium"
  "C:\SHIS"
) do (
  if not defined JDK17 if exist "%%~P\" (
    for /d %%D in ("%%~P\*") do (
      if not defined JDK17 call :accept "%%~D"
    )
  )
)
if defined JDK17 goto :run

echo [mvnw17] No JDK 17 or newer was found on this PC.
echo [mvnw17] Install one, e.g.:  winget install EclipseAdoptium.Temurin.17.JDK
echo [mvnw17] Or set JAVA17_HOME to an existing JDK 17+ folder.
exit /b 1

:run
set "JAVA_HOME=%JDK17%"
echo [mvnw17] JAVA_HOME = %JAVA_HOME%
call "%~dp0mvnw.cmd" %*
exit /b %ERRORLEVEL%

@REM -- :accept <dir>  -> sets JDK17 if <dir> holds a JDK version 17 or newer --
:accept
set "CAND=%~f1"
if not exist "%CAND%\bin\java.exe" exit /b 0
if not exist "%CAND%\release" exit /b 0
set "VER="
for /f "tokens=2 delims==" %%V in ('findstr /b /c:"JAVA_VERSION=" "%CAND%\release" 2^>nul') do set "VER=%%~V"
if not defined VER exit /b 0
set "MAJOR="
for /f "delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if not defined MAJOR exit /b 0
if %MAJOR% GEQ 17 set "JDK17=%CAND%"
exit /b 0
