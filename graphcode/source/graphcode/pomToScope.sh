#!/bin/bash

DIR=$(dirname $0)
DIR=$(realpath $DIR)

sed s/%group%/$1/ $DIR/pom_template.xml | sed s/%artifact%/$2/ | sed s/%version%/$3/ > /tmp/temp.xml

mvn dependency:sources -f /tmp/temp.xml > /dev/null 2>&1 
export CP=$(mvn dependency:build-classpath -f /tmp/temp.xml 2>/dev/null | grep -A 1 "Dependencies classpath" | tail -n 1)

echo $CP | gawk -f $DIR/pomToScope.awk | cat

cat <<EOF
Primordial,Java,stdlib,none
Primordial,Java,jarFile,primordial.jar.model
EOF


