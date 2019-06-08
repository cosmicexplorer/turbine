set -euxo pipefail

# Create semanticdb from Rsc
# Use semanticdb as input into Turbine
# Basically, create Java header jars from Scala header jars

mvn package

echo "class C" > C.scala
cat > D.java <<EOF
public class D {
  public static C foo() {
    return new C();
  }
}
EOF

# JAVALIB=/path/to/rt.jar
# SCALALIB=/path/to/scala-library.jar

JAVALIB="${JAVA_HOME}/jre/lib/rt.jar"

function merge_jars() {
 tr '\n' ':' | sed -re 's#:$##g'
}

SCALALIB="$(~/coursier fetch org.scala-lang:scala-library:2.12.8 | merge_jars)"

~/coursier launch com.twitter:rsc_2.12:0.0.0-768-7357aa0a --main rsc.cli.Main -- -cp $JAVALIB:$SCALALIB -artifacts semanticdb,scalasig -d . C.scala

java -cp ./target/turbine-0.1-SNAPSHOT-all-deps.jar com.google.turbine.main.Main --classpath $JAVALIB --output out.jar --semanticdbs META-INF/semanticdb/C.scala.semanticdb --sources D.java

jar -xvf out.jar

cat > E.scala <<EOF
class E extends C {
  def foo = D.foo()
  def accept(c: C) = ???

  accept(foo)
  accept(this)
}
EOF

~/coursier launch org.scala-lang:scala-compiler:2.12.8 --main scala.tools.nsc.Main -- -cp $JAVALIB:$SCALALIB:. E.scala
