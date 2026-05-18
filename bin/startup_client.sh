#!/bin/sh

#===========================================================================================
# Java Environment Setting
#===========================================================================================
error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

export BASE_DIR=$(dirname $0)/..
if [ -d "${BASE_DIR}/jdk" ] && [ -x "${BASE_DIR}/jdk/bin/java" ]; then
    JAVA_HOME="${BASE_DIR}/jdk"
fi

[ ! -e "$JAVA_HOME/bin/java" ] && error_exit "Please set the JAVA_HOME variable in your environment, We need java(x64)!"

export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export CLASSPATH=.:${CLASSPATH}:${BASE_DIR}/conf:${BASE_DIR}/lib/*

#===========================================================================================
# JVM Configuration
#===========================================================================================
JAVA_OPT="${JAVA_OPT} -server"
JAVA_OPT="${JAVA_OPT} -Xms224m -Xmx224m"
JAVA_OPT="${JAVA_OPT} -Xmn80m"
JAVA_OPT="${JAVA_OPT} -XX:MetaspaceSize=56m"
JAVA_OPT="${JAVA_OPT} -XX:MaxMetaspaceSize=112m"
JAVA_OPT="${JAVA_OPT} -XX:+UseParNewGC"
JAVA_OPT="${JAVA_OPT} -XX:+UseConcMarkSweepGC"
JAVA_OPT="${JAVA_OPT} -XX:CMSInitiatingOccupancyFraction=70"
JAVA_OPT="${JAVA_OPT} -XX:+CMSScavengeBeforeRemark"
JAVA_OPT="${JAVA_OPT} -XX:+UseCompressedOops"
JAVA_OPT="${JAVA_OPT} -Xss512k"
JAVA_OPT="${JAVA_OPT} -XX:SurvivorRatio=10"
JAVA_OPT="${JAVA_OPT} -XX:MaxTenuringThreshold=3"
JAVA_OPT="${JAVA_OPT} -XX:PretenureSizeThreshold=64k"
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -Djava.ext.dirs=$JAVA_HOME/jre/lib/ext"
JAVA_OPT="${JAVA_OPT} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPT="${JAVA_OPT} -Xdebug -Xrunjdwp:transport=dt_socket,address=9556,server=y,suspend=n"
JAVA_OPT="${JAVA_OPT} -cp ${CLASSPATH}"

# set JUXTAPOSE_HOME
if [ -z "$JUXTAPOSE_HOME" ] ; then
  ## resolve links - $0 may be a link to maven's home
  PRG="$0"

  # need this for relative symlinks
  while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
      PRG="$link"
    else
      PRG="`dirname "$PRG"`/$link"
    fi
  done

  saveddir=`pwd`

  JUXTAPOSE_HOME=`dirname "$PRG"`/..

  # make it fully qualified
  JUXTAPOSE_HOME=`cd "$JUXTAPOSE_HOME" && pwd`

  cd "$saveddir"
fi

export JUXTAPOSE_HOME

$JAVA ${JAVA_OPT} com.sunder.juxtapose.client.StandardClient $@
