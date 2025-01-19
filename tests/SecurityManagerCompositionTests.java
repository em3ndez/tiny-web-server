package tests;

import com.paulhammant.tiny.Tiny;
import com.sun.net.httpserver.HttpExchange;
import okhttp3.Response;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketPermission;
import java.security.AllPermission;
import java.util.List;
import java.net.URLPermission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class SecurityManagerCompositionTests {
    Tiny.WebServer webServer;
    boolean runFromTestSuite;

    {

        runFromTestSuite = Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(element -> element.toString().contains("tests.Suite.main"));

        only().describe("When additional composition can happen on a previously instantiated Tiny.WebServer", () -> {
            before(() -> {

                // compile ServerCompositionOne into hello1.jar
                compileAndPackage("ServerCompositionOne", "smtests1/ServerCompositionOne.class", "target/hello1.jar");
                // compile ServerCompositionTwo into hello2.jar
                compileAndPackage("ServerCompositionTwo", "smtests2/ServerCompositionTwo.class", "target/hello2.jar");

                System.err.println(">>> Tiny " + Tiny.class.getProtectionDomain().getCodeSource().getLocation());
                System.err.println(">>> Tiny cl " + Tiny.class.getClassLoader());

                webServer = new Tiny.WebServer(Tiny.Config.create()
                        .withHostAndWebPort("localhost", 8080));

                new Tiny.ClassLoader("target/hello1.jar")
                        .withComposition(webServer, "/one", "smtests1.ServerCompositionOne");

                new Tiny.ClassLoader("target/hello2.jar")
                       // .withPermissions(new URLPermission("https://httpbin.org/get", "GET:Accept"))
                        .withPermissions(new AllPermission())
                        .withComposition(webServer, "/two", "smtests2.ServerCompositionTwo");

                webServer.start();

            });
            it("Then /one/ONE/1 endpoint (doing its own http-get) should" + (runFromTestSuite ? " ": " not ") + "work as expected without " + (runFromTestSuite ? "a security manager": "an explicit grant from its security manager"), () -> {

                Response response1 = httpGet("/one/ONE/1");
                try (response1) {
                    assertThat(response1.code(), equalTo(200));
                    String body = response1.body().string();
                    if (runFromTestSuite) {
                        assertThat(body, equalTo("Hello /one/ONE/1 - https://httpbin.org/get returned json")); // No Security Manager setup
                    } else {
                        // with Security Manager in place, this should have a SecurityException which should be reflected on the GET response
                        assertThat(body, equalTo("Hello /one/ONE/1 - exception: access denied (\"java.net.URLPermission\" \"https://httpbin.org/get\" \"GET:Accept\")"));
                    }
                }

            });
            it("Then /two/TWO/2 endpoint (doing its own http-get) should work as expected " + (runFromTestSuite ? "without a security manager ": "if a security manager explicitly grants permission"), () -> {

                Response response2 = httpGet("/two/TWO/2");
                try (response2) {
                    assertThat(response2.code(), equalTo(200));
                    String body = response2.body().string();
                    assertThat(body, equalTo("Hello /two/TWO/2 - https://httpbin.org/get returned json")); // No Security Manager setup
                }

            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }

    private void compileAndPackage(String fileName, String classPath, String jarOutput) {
        try {
            Path tempDir = Files.createTempDirectory("my-compile-temp-" + fileName);

            Path originalFooFile = Paths.get("tests", fileName + ".foo");
            Path tempJavaFile = tempDir.resolve(fileName + ".java");
            Files.copy(originalFooFile, tempJavaFile, StandardCopyOption.REPLACE_EXISTING);

            compileJavaFile(tempJavaFile, "target/" + fileName + "_classes");
            createJarFile("target/" + fileName + "_classes", classPath, jarOutput);

            System.out.println("Successfully compiled and packaged " + fileName + " into " + jarOutput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void compileJavaFile(Path javaFile, String outputDir) throws IOException, InterruptedException {
        executeCommand("javac", "-cp", Tiny.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                  "-d", outputDir, javaFile.toAbsolutePath().toString());
    }

    private void createJarFile(String classesDir, String classPath, String jarOutput) throws IOException, InterruptedException {
        executeCommand("jar", "cf", jarOutput, "-C", classesDir, classPath);
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command);

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(command[0] + " failed with exit code " + exitCode);
        }
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Arrays.asList(
                SecurityManagerCompositionTests.class
        )), new DefaultReporter());
    }
}
