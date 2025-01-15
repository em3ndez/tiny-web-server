#!/bin/bash

# Tiny Booter script

test_libs=$(find ./test_libs -name '*.jar' | tr '\n' ':')
cp="${test_libs}./target/classes:./target/test-classes"
echo "CP\n${cp}\nCP"
EXEC="java -Djava.security.manager -Djava.security.debug=access:failure -Djava.security.policy=file:./sm/boot.policy -cp ${cp} tests.SecurityManagerCompositionTests"
echo $EXEC
$EXEC