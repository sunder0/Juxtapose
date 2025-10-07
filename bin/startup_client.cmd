cd ..
set JUXTAPOSE_HOME=%cd%
if not exist "%JUXTAPOSE_HOME%\bin\runjava_server.cmd" echo Please set the JUXTAPOSE_HOME variable in your environment! & EXIT /B 1
call "%JUXTAPOSE_HOME%\bin\runjava_server.cmd" com.sunder.juxtapose.client.StandardClient %*