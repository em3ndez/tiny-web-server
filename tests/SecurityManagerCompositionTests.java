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

    {
        only().describe("When additional composition can happen on a previously instantiated Tiny.WebServer", () -> {
            before(() -> {

                compileAndPackage("ServerCompositionOne", "smtests1/ServerCompositionOne.class", "target/hello1.jar");
                compileAndPackage("ServerCompositionTwo", "smtests2/ServerCompositionTwo.class", "target/hello2.jar");


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
        String[] javacCmd = new String[]{
                "javac",
                "-cp", Tiny.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                "-d", outputDir,
                javaFile.toAbsolutePath().toString()
        };
        executeCommand(javacCmd, "javac");
    }

    private void createJarFile(String classesDir, String classPath, String jarOutput) throws IOException, InterruptedException {
        String[] jarCmd = new String[]{
                "jar",
                "cf",
                jarOutput,
                "-C", classesDir,
                classPath
        };
        executeCommand(jarCmd, "jar");
    }

    private void executeCommand(String[] command, String commandName) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command);

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(commandName + " failed with exit code " + exitCode);
        }
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Arrays.asList(
                SecurityManagerCompositionTests.class
        )), new DefaultReporter());
    }
}
