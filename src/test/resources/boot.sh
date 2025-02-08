#!/bin/bash
mvn -q dependency:copy-dependencies   -DincludeScope=test   -DincludeArtifactIds=cuppa   -DoutputDirectory=target/libs_for_test
mvn -q dependency:copy-dependencies   -DincludeScope=test   -DincludeArtifactIds=okhttp   -DoutputDirectory=target/libs_for_test
mvn -q dependency:copy-dependencies   -DincludeScope=test   -DincludeArtifactIds=kotlin-stdlib   -DoutputDirectory=target/libs_for_test
mvn -q dependency:copy-dependencies   -DincludeScope=test   -DincludeArtifactIds=okio-jvm   -DoutputDirectory=target/libs_for_test
mvn -q dependency:copy-dependencies   -DincludeScope=test   -DincludeArtifactIds=hamcrest   -DoutputDirectory=target/libs_for_test
test_libs=$(find ./target/libs_for_test -name '*.jar' | tr '\n' ':')
cp="${test_libs}target/classes:target/test-classes"
java -Djava.security.manager -Djava.security.policy=file:src/test/resources/boot.policy -cp ${cp} tests.SecurityManagerCompositionTests