@color 0a
@title Openflower - Setup
@echo off
if not exist lib\fernflower.jar goto :noff
if not exist lib\javassist.jar goto :noja
if not exist tmp mkdir tmp
if not exist logs mkdir logs
rem call :title "compiling javassist"
rem if not exist lib\bin mkdir lib\bin
rem javac -cp lib/src -d lib/bin lib/src/javassist/bytecode/*.java
echo.
call :title "compiling utilities"
if not exist util\bin mkdir util\bin
javac -cp lib/javassist.jar -d util/bin util/src/*.java
echo.
call :title " running utilities "
set _ff_ver=fernflower_0.8.4
set _util_cp=lib/javassist.jar;util/bin
call :title16 [Flowerpatch]---
java -cp %_util_cp% Flowerpatch conf/%_ff_ver%-fixes.patch .static lib/fernflower.jar tmp/fix.jar
call :title16 [Mapper]--------
java -cp %_util_cp% Mapper conf/%_ff_ver%-mappings.txt tmp/fix.jar tmp/map.jar 1>logs\mapper.log
call :title16 [Accessory]-----
java -cp %_util_cp% Accessory tmp/map.jar tmp/acc.jar conf/%_ff_ver%-access.txt conf/%_ff_ver%-exceptions.txt 1>logs\accessory.log
call :title16 [Innerness]-----
java -cp %_util_cp% Innerness conf/%_ff_ver%-inner.txt tmp/acc.jar tmp/inn.jar 1>logs\innerness.log
echo.
pause
goto :eof

:noff
echo Please obtain an unmodified Fernflower 0.8.4 jar and place it under 'lib'.
pause
goto :eof

:noja
echo Please obtain javassist.jar and place it under 'lib'.
pause
goto :eof

:title16
echo ---%1------------------------------------------------------------
goto :eof

:title
echo ^>^>^> %~1 ^<^<^<
goto :eof

