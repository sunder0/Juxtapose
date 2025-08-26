#!/bin/sh

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

sh ${JUXTAPOSE_HOME}/bin/runjava_server.sh com.sunder.juxtapose.client.ClientBootstrap $@
