# Variables
PYTHON = python3
JAR = jar
JACOCO_AGENT = jacocoagent.jar
JACOCO_CLI = jacococli.jar
JACOCO_EXEC = jacoco.exec
JACOCO_REPORT = jacoco-report

# Default target
all: compile get-test-deps tests

# Compile Tiny.java
compile: target_classes
	javac -d target/classes Tiny.java

target_classes:
	mkdir -p target/classes

# Download test dependencies
get-test-deps:
	curl -s https://raw.githubusercontent.com/paul-hammant/mvn-dep-getter/refs/heads/main/mvn-dep-getter.py | $(PYTHON) - org.forgerock.cuppa:cuppa:1.7.0,org.hamcrest:hamcrest:3.0,com.squareup.okhttp3:okhttp:5.0.0-alpha.14,org.mockito:mockito-core:5.14.2,org.seleniumhq.selenium:selenium-java:4.26.0 test_libs

# Compile and run tests
tests: test-compile
	make compile
	java -cp "$$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" tests.Suite

test-compile: gen-test-list
	mkdir -p target/test-classes
	javac -d target/test-classes -cp "$$(find test_libs -name '*.jar' | tr '\n' ':')target/classes" @tests/sources.txt

gen-test-list:
	find tests -name "*.java" | sort > tests/sources.txt

# Clean build artifacts
clean:
	rm -rf target/classes target/test-classes @tests/sources.txt $(JACOCO_EXEC) $(JACOCO_REPORT)

# Instrument for coverage
coverage: $(JACOCO_AGENT) $(JACOCO_CLI)
	java -javaagent:$(JACOCO_AGENT)=destfile=$(JACOCO_EXEC) -cp "$$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" tests.Suite

# Generate coverage report
report: $(JACOCO_EXEC) $(JACOCO_CLI)
	mkdir target/srcForJaCoCo/com/paulhammant/tiny/
	cp Tiny.java target/srcForJaCoCo/com/paulhammant/tiny/
	java -jar $(JACOCO_CLI) report $(JACOCO_EXEC) --classfiles target/classes --sourcefiles target/srcForJaCoCo --html $(JACOCO_REPORT)

$(JACOCO_AGENT):
	curl -L -o $(JACOCO_AGENT) https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar

$(JACOCO_CLI):
	curl -L -o $(JACOCO_CLI) https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar

.PHONY: all compile get-test-deps tests clean coverage report
