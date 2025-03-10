package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static com.paulhammant.tiny.Tiny.toBytes;
import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bytesToString;

@Test
public class SeleniumTests {
    Tiny.WebServer webServer;

    {
        describe("When using Selenium to subscribe in a browser", () -> {

            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{

                    endPoint(Tiny.HttpMethods.GET, "/", (req, res, ctx) -> {
                        res.setHeader("Content-Type", "text/html");
                        res.sendResponse("""
                                <!DOCTYPE html>
                                <html lang="en">
                                <head><meta charset="UTF-8"><title>WebSocket Test</title></head>
                                <body>
                                    <h1>WebSocket Message Display (Selenium test)</h1>
                                    <pre id="messageDisplay"></pre>
                                </body>
                                <script>
                                const socket = new WebSocket('ws://localhost:8081/baz');
                                const messageDisplay = document.getElementById('messageDisplay');
    
                                socket.addEventListener('open', () => {
                                    socket.send('Hello WebSocket');
                                });
    
                                socket.addEventListener('message', (event) => {
                                    messageDisplay.textContent += event.data + '\\n';
                                    if (event.data.equals('stop')) {
                                        socket.close();
                                    }
                                });
    
                                </script>
                                </html>
                            """, 200);
                    });

                    webSocket("/baz", (message, sender, context) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + bytesToString(message) + "-" + i;
                            sender.sendBytesFrame(toBytes(responseMessage));
                            try {
                                sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        sender.sendBytesFrame(toBytes("stop"));
                    });
                }}.start();
            });

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                // To play with the wee browser app, uncomment this, and go to localhost:8080
//                try {
//                    Thread.sleep(600 * 1000);
//                } catch (InterruptedException e) {
//                }
                ChromeOptions options = new ChromeOptions();

                if (System.getProperty("headlessSelenium", "false").toLowerCase().contains("true")) {
                    options.addArguments("--headless=new"); // Use --headless=new for modern versions
                    options.addArguments("--no-sandbox");   // Required for running as root in a container
                    options.addArguments("--disable-dev-shm-usage"); // Prevents /dev/shm crashes
                    options.addArguments("--disable-gpu");  // Disable GPU acceleration (not needed for headless)
                    options.addArguments("--remote-allow-origins=*"); // Avoids connection issues

                }
                // Initialize WebDriver
                WebDriver driver = new ChromeDriver(options);
                try {
                    driver.get("http://localhost:8080/");
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    WebElement messageElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("messageDisplay")));
                    sleep(500);
                    String expectedMessages = "Server sent: Hello WebSocket-1\n" +
                            "Server sent: Hello WebSocket-2\n" +
                            "Server sent: Hello WebSocket-3\n" +
                            "stop";
                    assertThat(messageElement.getText(), equalTo(expectedMessages));
                } finally {
                    driver.quit();
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
