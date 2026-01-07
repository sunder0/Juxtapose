#!/bin/sh

#===========================================================================================
# Java Environment Setting
#===========================================================================================
error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=$HOME/jdk/java
[ ! -e "$JAVA_HOME/bin/java" ] && error_exit "Please set the JAVA_HOME variable in your environment, We need java(x64)!"

export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=$(dirname $0)/..
export CLASSPATH=.:${CLASSPATH}:${BASE_DIR}/conf:${BASE_DIR}/lib/*

#===========================================================================================
# JVM Configuration
#===========================================================================================
#-Xms初始堆内存大小 -Xmx最大堆内存大小 -Xmn新生代大小
JAVA_OPT="${JAVA_OPT} -server"
JAVA_OPT="${JAVA_OPT} -Xms224m -Xmx224m"
JAVA_OPT="${JAVA_OPT} -Xmn80m"
JAVA_OPT="${JAVA_OPT} -XX:MetaspaceSize=56m"
JAVA_OPT="${JAVA_OPT} -XX:MaxMetaspaceSize=112m"
JAVA_OPT="${JAVA_OPT} -XX:+UseParNewGC"         # 年轻代用ParNew
JAVA_OPT="${JAVA_OPT} -XX:+UseConcMarkSweepGC"  # 老年代用CMS
JAVA_OPT="${JAVA_OPT} -XX:CMSInitiatingOccupancyFraction=70"
JAVA_OPT="${JAVA_OPT} -XX:+CMSScavengeBeforeRemark" # Remark前先YGC
JAVA_OPT="${JAVA_OPT} -XX:+UseCompressedOops"
JAVA_OPT="${JAVA_OPT} -Xss512k"                 # 压缩线程大小
JAVA_OPT="${JAVA_OPT} -XX:SurvivorRatio=10"     # 更大Eden区
JAVA_OPT="${JAVA_OPT} -XX:MaxTenuringThreshold=3" # 更快晋升（代理对象生命周期短）
JAVA_OPT="${JAVA_OPT} -XX:PretenureSizeThreshold=64k" # 超过64大小的对象直接进入老年代
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -Djava.ext.dirs=$JAVA_HOME/jre/lib/ext"
JAVA_OPT="${JAVA_OPT} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPT="${JAVA_OPT} -Xdebug -Xrunjdwp:transport=dt_socket,address=9556,server=y,suspend=n"
JAVA_OPT="${JAVA_OPT} -cp ${CLASSPATH}"

numactl --interleave=all pwd > /dev/null 2>&1
if [ $? -eq 0 ]
then
	if [ -z "$RMQ_NUMA_NODE" ] ; then
		numactl --interleave=all $JAVA ${JAVA_OPT} $@
	else
		numactl --cpunodebind=$RMQ_NUMA_NODE --membind=$RMQ_NUMA_NODE $JAVA ${JAVA_OPT} $@
	fi
else
	$JAVA ${JAVA_OPT} $@
fi
