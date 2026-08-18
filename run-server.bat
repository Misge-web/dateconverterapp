@echo off
REM  Start the RMI server. Run compile.bat first.
echo Starting RMI Server on port 1099...
java -cp out rmi.DateConverterServer
