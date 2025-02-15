package tests;

import org.forgerock.cuppa.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.paulhammant.tiny.Tiny;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Test
public class ConfigTests {

    {
        describe("Config permutations", () -> {

            it("should create default config", () -> {
                Tiny.Config config = Tiny.Config.create();
                assertThat(config.inetSocketAddress, equalTo(null));
                assertThat(config.wsPort, equalTo(0));
                assertThat(config.wsBacklog, equalTo(50));
                assertThat(config.wsBindAddr, equalTo(null));
                assertThat(config.socketTimeoutMs, equalTo(30000));
            });

            it("should set web port", () -> {
               Tiny.Config config = Tiny.Config.create().withWebPort(8080);
                assertThat(config.inetSocketAddress.getPort(), equalTo(8080));
            });

            it("should set web socket port", () -> {
               Tiny.Config config = Tiny.Config.create().withWebSocketPort(8081);
                assertThat(config.wsPort, equalTo(8081));
            });

            it("should set web socket backlog", () -> {
               Tiny.Config config = Tiny.Config.create().withWsBacklog(100);
                assertThat(config.wsBacklog, equalTo(100));
            });

            it("should set host and web port", () -> {
               Tiny.Config config = Tiny.Config.create().withHostAndWebPort("localhost", 8080);
                assertThat(config.inetSocketAddress, equalTo(new InetSocketAddress("localhost", 8080)));
            });

            it("should set web socket bind address", () -> {
                InetAddress bindAddr = InetAddress.getLoopbackAddress();
               Tiny.Config config = Tiny.Config.create().withWsBindAddr(bindAddr);
                assertThat(config.wsBindAddr, equalTo(bindAddr));
            });

            it("should set socket timeout", () -> {
               Tiny.Config config = Tiny.Config.create().withSocketTimeoutMillis(60000);
                assertThat(config.socketTimeoutMs, equalTo(60000));
            });
        });
    }
}
