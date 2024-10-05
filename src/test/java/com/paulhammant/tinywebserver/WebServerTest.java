import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class WebServerTest {

    @Test
    public void shouldCreateWebServer() throws Exception {
        WebServer server = new WebServer(8080);
        assertThat(server, is(notNullValue()));
    }
}
