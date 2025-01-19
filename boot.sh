#!/bin/bash

# Tiny Booter script

test_libs=$(find ./test_libs -name '*.jar' | tr '\n' ':')
cp="${test_libs}target/classes:target/test-classes"
java -Djava.security.manager -Djava.security.policy=file:boot.policy -cp ${cp} tests.SecurityManagerCompositionTests