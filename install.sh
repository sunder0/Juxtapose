#git pull

#mvn versions:set -DnewVersion=1.0.10-SNAPSHOT
#mvn versions:commit

rm -rf target
rm -f devenv
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME=/opt/jiedaibao/java
fi
export PATH=/opt/jiedaibao/mvn/bin:$JAVA_HOME/bin:$PATH
mvn -Dmaven.test.skip=true clean package install assembly:assembly -U

#ln -s target/jiedaibao-antmq.dir/jiedaibao-antmq devenv
