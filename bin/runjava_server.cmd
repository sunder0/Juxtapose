if not exist "%JAVA_HOME%\bin\java.exe" echo Please set the JAVA_HOME variable in your environment, We need java(x64)! & EXIT /B 1
set "JAVA=%JAVA_HOME%\bin\java.exe"
setlocal
set BASE_DIR=%~dp0
set BASE_DIR=%BASE_DIR:~0,-1%
for %%d in (%BASE_DIR%) do set BASE_DIR=%%~dpd
set CLASSPATH=.;%BASE_DIR%conf;%CLASSPATH%;%BASE_DIR%lib\*;%BASE_DIR%lib\jna-lib\*
set "JAVA_OPT=%JAVA_OPT% -server -Xms2g -Xmx2g -Xmn1g -XX:PermSize=128m -XX:MaxPermSize=320m"
set "JAVA_OPT=%JAVA_OPT% -XX:+UseConcMarkSweepGC -XX:+UseCMSCompactAtFullCollection -XX:CMSInitiatingOccupancyFraction=70 -XX:+CMSParallelRemarkEnabled -XX:SoftRefLRUPolicyMSPerMB=0 -XX:+CMSClassUnloadingEnabled -XX:SurvivorRatio=8 -XX:-UseParNewGC"
set "JAVA_OPT=%JAVA_OPT% -verbose:gc -Xloggc:"%USERPROFILE%\rmq_srv_gc.log" -XX:+PrintGCDetails"
set "JAVA_OPT=%JAVA_OPT% -XX:-OmitStackTraceInFastThrow"
set "JAVA_OPT=%JAVA_OPT% -XX:-UseLargePages"
set "JAVA_OPT=%JAVA_OPT% -Djava.ext.dirs=$JAVA_HOME\jre\lib\ext"
set "JAVA_OPT=%JAVA_OPT% -cp "%CLASSPATH%""
"%JAVA%" %JAVA_OPT% %*