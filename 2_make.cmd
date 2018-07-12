@color 0a
@title Openflower - Make
@echo off
if not exist src goto :nosrc
if not exist bin mkdir bin
javac -cp src -d bin src/de/fernflower/main/decompiler/*.java
pause
goto :eof

:nosrc
echo Please run decompile first!
pause

