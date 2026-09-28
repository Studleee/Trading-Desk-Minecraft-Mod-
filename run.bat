@echo off
rem Launches Minecraft with your mod loaded.
if exist "%USERPROFILE%\.jdks\jdk-25.0.4.1+1" set "JAVA_HOME=%USERPROFILE%\.jdks\jdk-25.0.4.1+1"
call "%~dp0gradlew.bat" runClient
