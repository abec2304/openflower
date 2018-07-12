@color 0a
@title Openflower - Decompile
@echo off
if exist src goto :src
if not exist tmp\inn.jar goto :nosetup
java -jar tmp/fix.jar -bto=0 -log=ERROR tmp/inn.jar tmp/dec
if not exist dec mkdir dec
cd dec
jar xf ../tmp/dec/inn.jar
cd ..
xcopy /s dec src\ 1>logs\xcopy.log
pause
goto :eof

:nosetup
echo Please run setup first!
pause
goto :eof

:src
echo "src" already exists, will not decompile.
pause

