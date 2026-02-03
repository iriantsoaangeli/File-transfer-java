@echo off
REM Network File Transfer Application - Windows Startup Script

echo ======================================
echo   Network File Transfer Application
echo          miyabi69 Protocol
echo ======================================
echo.

REM Check if nmap is installed
where nmap >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: nmap is not installed!
    echo Please install nmap from: https://nmap.org/download.html
    echo Or use: choco install nmap
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('nmap --version ^| findstr /r "Nmap version"') do set NMAP_VERSION=%%i
echo ✓ nmap found: %NMAP_VERSION%

REM Check if Java 17+ is available
java -version >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed!
    pause
    exit /b 1
)

for /f "tokens=1-3" %%a in ('java -version 2^>^&1 ^| findstr /r "version"') do set JAVA_VER=%%c
set JAVA_VER=%JAVA_VER:"=%
for /f "tokens=1 delims=." %%a in ("%JAVA_VER%") do set JAVA_MAJOR=%%a

if %JAVA_MAJOR% lss 17 (
    echo ERROR: Java 17 or higher is required!
    echo Current version: %JAVA_VER%
    pause
    exit /b 1
)

echo ✓ Java found: %JAVA_VER%

REM Check if Maven is available
mvn -version >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed!
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('mvn -version ^| findstr /r "Apache Maven"') do set MAVEN_VERSION=%%i
echo ✓ Maven found: %MAVEN_VERSION%
echo.

REM Create necessary directories
if not exist logs mkdir logs
if not exist mailbox mkdir mailbox

echo Starting application...
echo.

REM Run the application
mvn clean javafx:run