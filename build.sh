#!/bin/bash

set -e

function compile() {
    echo "Compiling TinyWeb.java..."
    mkdir -p target/classes
    javac -d target/classes/ TinyWeb.java
    echo "Compilation complete."
}

function get_test_deps() {
    echo "Downloading dependencies..."
    curl -s https://raw.githubusercontent.com/paul-hammant/mvn-dep-getter/refs/heads/main/mvn-dep-getter.py | python3 - org.forgerock.cuppa:cuppa:1.7.0,org.hamcrest:hamcrest:3.0,com.squareup.okhttp3:okhttp:5.0.0-alpha.14,org.mockito:mockito-core:5.14.2,org.seleniumhq.selenium:selenium-java:4.26.0 test_libs
    echo "Dependencies downloaded."
}

function tests() {
    echo "Compiling tests..."
    mkdir -p target/test-classes
    find tests -name "*.java" > tests/sources.txt
    javac -d target/test-classes -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/classes" @tests/sources.txt
    echo "Running tests..."
    java -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" tests.Suite
}

case "$1" in
    compile)
        compile
        ;;
    get-test-deps)
        get_test_deps
        ;;
    tests)
        tests
        ;;
    *)
        echo "Usage: $0 {compile|get-test-deps|tests}"
        exit 1
        ;;
esac
