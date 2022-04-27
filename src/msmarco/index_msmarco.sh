#!/bin/bash

MSMARCO=$(pwd)
MSMARCO=$(dirname $MSMARCO)
COLL=$MSMARCO/data/training/
INDEX=$MSMARCO/index/

cat > ../index.msmarco.properties << EOF1
coll=$COLL
index=$INDEX
stopfile=stop.txt
parser=line_simple
EOF1

cd ..
mvn exec:java@index -Dexec.args="index.msmarco.properties"
cd - || exit
