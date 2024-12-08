package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bytesToString;
import static tests.Suite.toBytes;

@Test
public class IntegrationTests {
    TinyWeb.Server webServer;

    {
        only().describe("When using Selenium to subscribe in a browser", () -> {

            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081) {{
                    endPoint(TinyWeb.Method.GET, "/javascriptWebSocketClient.js", new TinyWeb.JavascriptSocketClient());

                    endPoint(TinyWeb.Method.GET, "/", (req, res, ctx) -> {
                        res.setHeader("Content-Type", "text/html");
                        res.sendResponse("""
                                <!DOCTYPE html>
                                <html lang="en">
                                <head>
                                    <meta charset="UTF-8">
                                    <title>WebSocket Test</title>
                                    <script src="/javascriptWebSocketClient.js"></script>
                                </head>
                                <body>
                                    <h1>WebSocket Message Display</h1>
                                    <pre id="messageDisplay"></pre>
                                </body>
                                <script>
                                console.log("hello 1");
                                
                                const tinyWebSocketclient = new TinyWeb.SocketClient('localhost', 8081);
                                
                                async function example() {
                                    try {
                                        console.log("WebSocket readyState before open:", tinyWebSocketclient.socket.readyState);
                                        await tinyWebSocketclient.waitForOpen();
                                        console.log("WebSocket readyState after open:", tinyWebSocketclient.socket.readyState);
                                        await tinyWebSocketclient.sendMessage('/baz', 'Hello WebSocket');
                                        
                                        tinyWebSocketclient.receiveMessages("stop", (response) => {
                                            console.log("Received message:", response);
                                            if (response) {
                                                document.getElementById('messageDisplay').textContent += (response + "\\n");
                                            }
                                        });
                                        await tinyWebSocketclient.close();
                                    } catch (error) {
                                        console.error('WebSocket error:', error);
                                    }
                                }

                                example();
                                </script>
                                </html>
                            """, 200);
                    });

                    webSocket("/baz", (message, sender) -> {
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

                WebDriver driver = new ChromeDriver();
                try {
                    driver.get("http://localhost:8080/");
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    WebElement messageElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("messageDisplay")));
                    sleep(500);
                    String expectedMessages = "Server sent: Hello WebSocket-1\n" +
                            "Server sent: Hello WebSocket-2\n" +
                            "Server sent: Hello WebSocket-3";
                    assertThat(messageElement.getText(), equalTo(expectedMessages));
                } finally {
                    //driver.quit();
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
