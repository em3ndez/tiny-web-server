# TinyWebTest Examples

## Echoing GET Endpoint
// describe("Echoing GET endpoint respond with..", () -> {
//     it("should return user profile for Jimmy", () -> {
try (Response response = httpGet("http://localhost:8080/users/Jimmy")) {
    assertThat(response.body().string(), equalTo("User profile: Jimmy"));
}
//     it("should return user profile for Thelma", () -> {
try (Response response = httpGet("http://localhost:8080/users/Thelma")) {
    assertThat(response.body().string(), equalTo("User profile: Thelma"));
}

## Nested Path with Parameterized Parts
// describe("Nested path with parameterized parts", () -> {
//     it("should extract parameters correctly from nested path", () -> {
try (Response response = httpGet("http://localhost:8080/api/v1/items/123/details/456")) {
    assertThat(response.body().string(), equalTo("Item: 123, Detail: 456\n/api->/v1->itemz."));
    assertThat(response.code(), equalTo(200));
}
try (Response response = httpGet("http://localhost:8080/api/v1/items/abc/details/def")) {
    assertThat(response.body().string(), equalTo("Item: abc, Detail: def\n/api->/v1->itemz.itemz."));
    assertThat(response.code(), equalTo(200));
}
//     it("should return 404 for incorrect nested path", () -> {
try (Response response = httpGet("http://localhost:8080/api/v1/items/123/456")) {
    assertThat(response.body().string(), equalTo("Not found"));
    assertThat(response.code(), equalTo(404));
}

## Filtering
// describe("Filtering", () -> {
//     it("should allow access when header 'sucks' is absent", () -> {
try (Response response = httpGet("http://localhost:8080/foo/bar")) {
    assertThat(response.code(), equalTo(200));
    assertThat(response.body().string(), equalTo("Hello, World!"));
}
//     it("should deny access when header 'sucks' is present", () -> {
try (Response response = httpGet("http://localhost:8080/foo/bar", "sucks", "true")) {
    assertThat(response.code(), equalTo(403));
    assertThat(response.body().string(), equalTo("Access Denied"));
}

## Static File Serving
// describe("Static file serving functionality", () -> {
//     it("should return 200 and serve a text file", () -> {
try (Response response = httpGet("http://localhost:8080/static/README.md")) {
    assertThat(response.code(), equalTo(200));
    assertThat(response.body().contentType().toString(), equalTo("text/markdown"));
    assertThat(response.body().string(), containsString("hello"));
}
//     it("should return 404 for non-existent files", () -> {
try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
    assertThat(response.code(), equalTo(404));
    assertThat(response.body().string(), containsString("Not found"));
}
//     it("should return 200 and serve a file from a subdirectory", () -> {
try (Response response = httpGet("http://localhost:8080/static/src/main/java/com/paulhammant/tinywebserver/TinyWeb.java")) {
    assertThat(response.code(), equalTo(200));
    assertThat(response.body().contentType().toString(), equalTo("text/x-java"));
    assertThat(response.body().string(), containsString("class"));
}
//     it("should return 200 and serve a non-text file", () -> {
try (Response response = httpGet("http://localhost:8080/static/target/classes/com/paulhammant/tinywebserver/TinyWeb$Server.class")) {
    assertThat(response.code(), equalTo(200));
    assertThat(response.body().contentType().toString(), equalTo("application/java-vm"));
    assertThat(response.body().string(), containsString("(Lcom/sun/net/httpserver/HttpExchange;ILjava/lang/String;)V"));
}

## WebSocket Communication
// describe("TinyWeb.SocketServer with TinyWeb.Server", () -> {
//     it("should echo three messages plus -1 -2 -3 back to the client", () -> {
try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
    client.performHandshake();
    client.sendMessage("/foo/baz", "Hello WebSocket");

    StringBuilder sb = new StringBuilder();

    // Read all three response frames
    for (int i = 0; i < 3; i++) {
        String response = client.receiveMessage();
        if (response != null) {
            sb.append(response);
        }
    }
    assertThat(sb.toString(), equalTo(
            "Server sent: Hello WebSocket-1" +
                    "Server sent: Hello WebSocket-2" +
                    "Server sent: Hello WebSocket-3"));
}
