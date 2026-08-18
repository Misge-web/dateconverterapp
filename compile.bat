@echo off
REM ============================================================
REM  Compile all sources into out\
REM  Pure Java (javax.swing + java.rmi) — no external deps.
REM ============================================================

if not exist out mkdir out

echo Compiling...
javac -d out ^
  src\rmi\DateConverterException.java ^
  src\rmi\ConversionLogger.java ^
  src\rmi\DateConverterService.java ^
  src\rmi\DateConverterServiceImpl.java ^
  src\rmi\DateConverterServer.java ^
  src\client\DateConverterClient.java ^
  src\client\DateConverterUI.java

if %ERRORLEVEL% == 0 (
    echo Compilation successful.
) else (
    echo Compilation FAILED.
    exit /b 1
)
