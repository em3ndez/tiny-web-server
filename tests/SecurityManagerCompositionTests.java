package tests;

import com.paulhammant.tiny.Tiny;
import com.sun.net.httpserver.HttpExchange;
import okhttp3.Response;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketPermission;
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

    {
        only().describe("When additional composition can happen on a previously instantiated Tiny.WebServer", () -> {
            before(() -> {

                try {

                    Path tempDir = Files.createTempDirectory("my-compile-temp-");

                    Path originalFooFile = Paths.get("tests", "ServerCompositionOne.foo");
                    Path tempJavaFile = tempDir.resolve("ServerCompositionOne.java");
                    Files.copy(originalFooFile, tempJavaFile, StandardCopyOption.REPLACE_EXISTING);



                    // 1) Compile .foo to .class
                    String[] javacCmd = new String[] {
                            "javac",
                            "-cp", Tiny.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                            "-d", "target/ServerCompositionOne_classes",
                            tempJavaFile.toAbsolutePath().toString()
                    };
                    Process javacProcess = Runtime.getRuntime().exec(javacCmd);

                    // Read and print errors from the compiler
                    try (BufferedReader errorReader =
                                 new BufferedReader(new InputStreamReader(javacProcess.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println(line); // Print javac errors to standard error
                        }
                    }

                    int javacExitCode = javacProcess.waitFor();
                    if (javacExitCode != 0) {
                        throw new RuntimeException("javac failed with exit code " + javacExitCode);
                    }

                    // 2) Create the jar containing only tests/ServerCompositionTwo.class
                    String[] jarCmd = new String[] {
                            "jar",
                            "cf",                       // create file
                            "target/hello1.jar",    // jar output
                            "-C", "target/ServerCompositionOne_classes",     // change to target/classes
                            "smtests1/ServerCompositionOne.class"  // only package up this .class
                    };
                    Process jarProcess = Runtime.getRuntime().exec(jarCmd);
                    int jarExitCode = jarProcess.waitFor();
                    if (jarExitCode != 0) {
                        throw new RuntimeException("jar creation failed with exit code " + jarExitCode);
                    }

                    System.out.println("Successfully compiled and packaged ServerCompositionOne into target/hello1.jar");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {

                    Path tempDir = Files.createTempDirectory("my-compile-temp-2");

                    Path originalFooFile = Paths.get("tests", "ServerCompositionTwo.foo");
                    Path tempJavaFile = tempDir.resolve("ServerCompositionTwo.java");
                    Files.copy(originalFooFile, tempJavaFile, StandardCopyOption.REPLACE_EXISTING);


                    // 1) Compile .foo to .class
                    String[] javacCmd = new String[] {
                            "javac",
                            "-cp", Tiny.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                            "-d", "target/ServerCompositionTwo_classes",
                            tempJavaFile.toAbsolutePath().toString()
                    };
                    Process javacProcess = Runtime.getRuntime().exec(javacCmd);

                    // Read and print errors from the compiler
                    try (BufferedReader errorReader =
                                 new BufferedReader(new InputStreamReader(javacProcess.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println(line); // Print javac errors to standard error
                        }
                    }

                    int javacExitCode = javacProcess.waitFor();
                    if (javacExitCode != 0) {
                        throw new RuntimeException("javac failed with exit code " + javacExitCode);
                    }

                    // 2) Create the jar containing only tests/ServerCompositionTwo.class
                    String[] jarCmd = new String[] {
                            "jar",
                            "cf",                       // create file
                            "target/hello2.jar",    // jar output
                            "-C", "target/ServerCompositionTwo_classes",     // change to target/classes
                            "smtests2/ServerCompositionTwo.class"  // only package up this .class
                    };
                    Process jarProcess = Runtime.getRuntime().exec(jarCmd);
                    int jarExitCode = jarProcess.waitFor();
                    if (jarExitCode != 0) {
                        throw new RuntimeException("jar creation failed with exit code " + jarExitCode);
                    }

                    System.out.println("Successfully compiled and packaged ServerCompositionTwo into targetib/hello2.jar");
                } catch (Exception e) {
                    e.printStackTrace();
                }


                System.err.println(">>>" + Tiny.class.getProtectionDomain().getCodeSource().getLocation());

                webServer = new Tiny.WebServer(Tiny.Config.create()
                        .withHostAndWebPort("localhost", 8080));

                new Tiny.ClassLoader(SecurityManagerCompositionTests.class.getClassLoader(), "target/hello1.jar")
                        .withPermissions(new SocketPermission("httpbin.org:443", "connect"),
                                new URLPermission("https://httpbin.org/get", "GET:Accept"),
                                new SocketPermission("httpbin.org:443", "resolve"))
                        .withComposition(webServer, "/one", "smtests1.ServerCompositionOne");

                new Tiny.ClassLoader(SecurityManagerCompositionTests.class.getClassLoader(), "target/hello2.jar")
                        .withComposition(webServer, "/two", "smtests2.ServerCompositionTwo");

                webServer.start();

            });
            it("Then both endpoints should be accessible via GET", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/one/ONE"),
                        "Hello One - https://httpbin.org/get returned json", 200);
                Response response = httpGet("/two/TWO");
                try (response) {
                    assertThat(response.code(), equalTo(200));
                    String body = response.body().string();
                    boolean runFromTestSuite = Arrays.stream(Thread.currentThread().getStackTrace())
                            .anyMatch(element -> element.toString().contains("tests.Suite.main"));
                    if (runFromTestSuite) {
                        assertThat(body, equalTo("Hello Two - https://httpbin.org/get returned json"));
                    } else {
                        throw new AssertionError("should experience effects of security manager: " + body);
                        //TODO: asserThat(..) yet to do
                    }
                }
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Arrays.asList(
                SecurityManagerCompositionTests.class
        )), new DefaultReporter());
    }
}