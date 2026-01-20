set BASE_DIR=%~dp0
set BASE_DIR=%BASE_DIR:~0,-1%
for %%d in (%BASE_DIR%) do set BASE_DIR=%%~dpd
if exist "%BASE_DIR%\jdk\bin\java.exe" (
    set "JAVA_HOME=%BASE_DIR%\jdk"
)

if not exist "%JAVA_HOME%\bin\java.exe" echo Please set the JAVA_HOME variable in your environment, We need java(x64)! & EXIT /B 1
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "JUXTAPOSE_HOME=%BASE_DIR%"
set CLASSPATH=.;%BASE_DIR%conf;%CLASSPATH%;%BASE_DIR%lib\*;%JAVA_HOME%\jre\lib\ext\*
set "JAVA_OPT=%JAVA_OPT% -server"
set "JAVA_OPT=%JAVA_OPT% -Xms224m -Xmx224m"
set "JAVA_OPT=%JAVA_OPT% -Xmn80m"
set "JAVA_OPT=%JAVA_OPT% -XX:MetaspaceSize=56m"
set "JAVA_OPT=%JAVA_OPT% -XX:MaxMetaspaceSize=112m"
set "JAVA_OPT=%JAVA_OPT% -XX:+UseParNewGC"
set "JAVA_OPT=%JAVA_OPT% -XX:+UseConcMarkSweepGC"
set "JAVA_OPT=%JAVA_OPT% -XX:CMSInitiatingOccupancyFraction=70"
set "JAVA_OPT=%JAVA_OPT% -XX:+CMSScavengeBeforeRemark"
set "JAVA_OPT=%JAVA_OPT% -XX:+UseCompressedOops"
set "JAVA_OPT=%JAVA_OPT% -Xss512k"
set "JAVA_OPT=%JAVA_OPT% -XX:SurvivorRatio=10"
set "JAVA_OPT=%JAVA_OPT% -XX:MaxTenuringThreshold=3"
set "JAVA_OPT=%JAVA_OPT% -XX:PretenureSizeThreshold=64k"
set "JAVA_OPT=%JAVA_OPT% -XX:-OmitStackTraceInFastThrow"
set "JAVA_OPT=%JAVA_OPT% -XX:-UseLargePages"
set "JAVA_OPT=%JAVA_OPT% -Djava.ext.dirs=$JAVA_HOME\jre\lib\ext"
set "JAVA_OPT=%JAVA_OPT% -cp "%CLASSPATH%""
"%JAVA%" %JAVA_OPT% com.sunder.juxtapose.client.StandardClient %*