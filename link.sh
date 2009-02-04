#!/bin/sh

COMMON_LIBS=`find ./lib -name '*.jar' -printf '%p:'`
PC_LIBS=`find ./pc-lib -name '*.jar' -printf '%p:'`
NXT_LIBS=`find ./nxt-lib -name '*.jar' -printf '%p:'`

RUN_CLASSPATH="$COMMON_LIBS$PC_LIBS"
NXT_CLASSPATH="$COMMON_LIBS$NXT_LIBS./nxt-build"

/usr/bin/env java -cp $RUN_CLASSPATH -Dnxj.home="$PWD" js.tinyvm.TinyVM \
  --writeorder LE --classpath $NXT_CLASSPATH \
  $@
