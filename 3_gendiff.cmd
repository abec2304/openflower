@color 0a
@title Openflower - GenDiff
@echo off
if not exist dec goto :nodec
diff -r -U3 dec\de src\de > dev.diff
pause
goto :eof

:nodec
echo Please run decompile first!
pause

