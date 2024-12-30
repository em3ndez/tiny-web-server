# Variables
JAVAC = javac
JAVA = java
CURL = curl
PYTHON = python3
MKDIR = mkdir -p
FIND = find
CP = cp
JAR = jar
TARGET_CLASSES = target/classes
TARGET_TEST_CLASSES = target/test-classes
TEST_LIBS = test_libs
SOURCES_FILE = tests/sources.txt
MAIN_CLASS = tests.Suite
JACOCO_AGENT = jacocoagent.jar
JACOCO_CLI = jacococli.jar
JACOCO_EXEC = jacoco.exec
JACOCO_REPORT = jacoco-report

# Default target
all: compile get-test-deps tests

# Compile Tiny.java
compile: $(TARGET_CLASSES)
	$(JAVAC) -d $(TARGET_CLASSES) Tiny.java

$(TARGET_CLASSES):
	$(MKDIR) $(TARGET_CLASSES)

# Download test dependencies
get-test-deps:
	$(CURL) -s https://raw.githubusercontent.com/paul-hammant/mvn-dep-getter/refs/heads/main/mvn-dep-getter.py | $(PYTHON) - org.forgerock.cuppa:cuppa:1.7.0,org.hamcrest:hamcrest:3.0,com.squareup.okhttp3:okhttp:5.0.0-alpha.14,org.mockito:mockito-core:5.14.2,org.seleniumhq.selenium:selenium-java:4.26.0 $(TEST_LIBS)

# Compile and run tests
tests: test-compile
	$(MAKE) compile
	$(JAVA) -cp "$$(find $(TEST_LIBS) -name '*.jar' | tr '\n' ':')$(TARGET_TEST_CLASSES):$(TARGET_CLASSES)" $(MAIN_CLASS)

test-compile: gen-test-list
	$(MKDIR) $(TARGET_TEST_CLASSES)
	$(JAVAC) -d $(TARGET_TEST_CLASSES) -cp "$$(find $(TEST_LIBS) -name '*.jar' | tr '\n' ':')$(TARGET_CLASSES)" @$<

gen-test-list:
	$(FIND) tests -name "*.java" | sort > $@

# Clean build artifacts
clean:
	rm -rf $(TARGET_CLASSES) $(TARGET_TEST_CLASSES) $(SOURCES_FILE) $(JACOCO_EXEC) $(JACOCO_REPORT)

# Instrument for coverage
coverage: $(JACOCO_AGENT) $(JACOCO_CLI)
	$(JAVA) -javaagent:$(JACOCO_AGENT)=destfile=$(JACOCO_EXEC) -cp "$$(find $(TEST_LIBS) -name '*.jar' | tr '\n' ':')$(TARGET_TEST_CLASSES):$(TARGET_CLASSES)" $(MAIN_CLASS)

# Generate coverage report
report: $(JACOCO_EXEC) $(JACOCO_CLI)
	$(MKDIR) target/srcForJaCoCo/com/paulhammant/tiny/
	$(CP) Tiny.java target/srcForJaCoCo/com/paulhammant/tiny/
	$(JAVA) -jar $(JACOCO_CLI) report $(JACOCO_EXEC) --classfiles $(TARGET_CLASSES) --sourcefiles target/srcForJaCoCo --html $(JACOCO_REPORT)

$(JACOCO_AGENT):
	$(CURL) -L -o $(JACOCO_AGENT) https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar

$(JACOCO_CLI):
	$(CURL) -L -o $(JACOCO_CLI) https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar

.PHONY: all compile get-test-deps tests clean coverage report
