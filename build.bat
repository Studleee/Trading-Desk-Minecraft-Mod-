@echo off
rem Builds the mod jar into build\libs so you can share it or drop it in a mods folder.
if exist "%USERPROFILE%\.jdks\jdk-25.0.4.1+1" set "JAVA_HOME=%USERPROFILE%\.jdks\jdk-25.0.4.1+1"
call "%~dp0gradlew.bat" build
