// [Previous code remains the same until main method]

    public static void main(String[] args) throws IOException {
        new App() {{
            // Root routes
            handler(Method.GET, "/", (req, res, params) -> handleRoot(req, res, params));
            
            // Login endpoint
            handler(Method.POST, "/login", (req, res, params) -> handleLogin(req, res, params));
            
            // User routes with MustBeLoggedIn filter
            path("/users") {{
                filter((req, res, params) -> authenticateBasicAuth(req, res, params));
                
                handler(Method.GET, "", (req, res, params) -> handleListUsers(req, res, params));
                
                handler(Method.GET, "/(\\w+)", (req, res, params) -> handleGetUserProfile(req, res, params));
                
                end();
            }};
            
            // API routes with MustHaveValidToken filter
            path("/api") {{
                filter((req, res, params) -> authenticateToken(req, res, params));
                
                path("/v1") {{
                    handler(Method.GET, "/status", (req, res, params) -> handleApiStatus(req, res, params));
                    
                    end();
                }};
                
                end();
            }};
            
            start();
            
            System.out.println("Server running on port 8080");
        }};
    }
    
    private static class App extends WebServer {
        public App() throws IOException {
            super(8080);
        }
        
        private void handleRoot(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("Hello, World!");
        }
        
        private void handleLogin(Request req, Response res, Map<String, String> params) throws IOException {
            String[] credentials = new String(
                Base64.getDecoder().decode(
                    req.getAuthHeader().orElse("").replace("Basic ", "")
                )
            ).split(":");
            
            if (credentials.length == 2) {
                String username = credentials[0];
                String password = credentials[1];
                
                if (password.length() > 3) {
                    loggedInUsers.add(username);
                    String token = Base64.getEncoder().encodeToString(
                        (username + ":" + System.currentTimeMillis()).getBytes()
                    );
                    validTokens.put(token, username);
                    res.write("Token: " + token);
                    return;
                }
            }
            res.write("Invalid credentials", 401);
        }
        
        private boolean authenticateBasicAuth(Request req, Response res, Map<String, String> params) throws IOException {
            String[] credentials = new String(
                Base64.getDecoder().decode(
                    req.getAuthHeader().orElse("").replace("Basic ", "")
                )
            ).split(":");
            
            if (credentials.length == 2 && loggedInUsers.contains(credentials[0])) {
                req.setAttribute("username", credentials[0]);
                return true;
            }
            res.write("Must be logged in", 401);
            return false;
        }
        
        private void handleListUsers(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("List users - logged in as: " + req.getAttribute("username"));
        }
        
        private void handleGetUserProfile(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("User profile: " + params.get("1") + 
                     " (viewed by: " + req.getAttribute("username") + ")");
        }
        
        private boolean authenticateToken(Request req, Response res, Map<String, String> params) throws IOException {
            String token = req.getAuthHeader().orElse("")
                .replace("Bearer ", "");
            
            if (validTokens.containsKey(token)) {
                req.setAttribute("username", validTokens.get(token));
                return true;
            }
            res.write("Invalid token", 401);
            return false;
        }
        
        private void handleApiStatus(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("API v1 Status: OK (user: " + req.getAttribute("username") + ")");
        }
    }
