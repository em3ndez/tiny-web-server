package com.paulhammant.tinywebserver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;
public class SocketServer {

    @FunctionalInterface
    public interface SocketMessageHandler {
        void handleMessage(byte[] message, MessageSender sender) throws IOException;
    }

    public static class MessageSender {
        private final OutputStream outputStream;

        public MessageSender(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void sendTextFrame(byte[] payload) throws IOException {
            outputStream.write(0x81); // FIN bit set, text frame
            if (payload.length < 126) {
                outputStream.write(payload.length);
            } else if (payload.length <= 65535) {
                outputStream.write(126);
                outputStream.write((payload.length >> 8) & 0xFF);
                outputStream.write(payload.length & 0xFF);
            } else {
                outputStream.write(127);
                for (int i = 7; i >= 0; i--) {
                    outputStream.write((payload.length >> (8 * i)) & 0xFF);
                }
            }
            outputStream.write(payload);
            outputStream.flush();
        }
    }

    private final int port;
    private ServerSocket server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds timeout
    private static final SecureRandom random = new SecureRandom();
    private Map<String, SocketMessageHandler> messageHandlers = new HashMap<>();

    public SocketServer(int port) {
        this.port = port;
    }

    public void registerMessageHandler(String path, SocketMessageHandler handler) {
        this.messageHandlers.put(path, handler);
    }

    public void start() {
        try {
            server = new ServerSocket(port);
            System.out.println("WebSocket server started on port " + port);

            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    client.setSoTimeout(SOCKET_TIMEOUT);
                    System.out.println("A client connected.");

                    executor.execute(() -> handleClient(client));
                } catch (SocketException e) {
                    if (e.getMessage().equals("Socket closed")) {
                        // likely just the server being shut down programmatically.
                    } else {
                        throw e;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Error accepting client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Can't start WebSocket Server", e);
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            Scanner s = new Scanner(in, "UTF-8");

            try {
                String data = s.useDelimiter("\\r\\n\\r\\n").next();
                Matcher get = Pattern.compile("^GET").matcher(data);

                if (get.find()) {
                    System.out.println("Received handshake data: " + data);
                    Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                    if (!match.find()) {
                        client.close();
                        return;
                    }

                    // Handle handshake
                    String acceptKey = generateAcceptKey(match.group(1));
                    sendHandshakeResponse(out, acceptKey);

                    // Handle WebSocket communication
                    handleWebSocketCommunication(client, in, out);
                }
            } finally {
                s.close();
                client.close();
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private String generateAcceptKey(String webSocketKey) throws UnsupportedEncodingException {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                    .getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendHandshakeResponse(OutputStream out, String acceptKey) throws IOException {
        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: websocket\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n").getBytes("UTF-8");

        out.write(response);
        out.flush();
    }

    private void handleWebSocketCommunication(Socket client, InputStream in, OutputStream out) throws IOException {
        MessageSender sender = new MessageSender(out);
        byte[] buffer = new byte[8192];

        while (!client.isClosed()) {
            // Read frame header
            int headerLength = readFully(in, buffer, 0, 2);
            if (headerLength < 2) break;

            boolean fin = (buffer[0] & 0x80) != 0;
            int opcode = buffer[0] & 0x0F;
            boolean masked = (buffer[1] & 0x80) != 0;
            int payloadLength = buffer[1] & 0x7F;
            int offset = 2;

            System.out.println("Frame Header - FIN: " + fin + ", Opcode: " + opcode +
                    ", Masked: " + masked + ", Initial PayloadLength: " + payloadLength);

            // Handle extended payload length cases
            if (payloadLength == 126) {
                headerLength = readFully(in, buffer, offset, 2);
                if (headerLength < 2) break;
                payloadLength = ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
                offset += 2;
            } else if (payloadLength == 127) {
                headerLength = readFully(in, buffer, offset, 8);
                if (headerLength < 8) break;
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength |= (buffer[offset + i] & 0xFF) << ((7 - i) * 8);
                }
                offset += 8;
            }

            System.out.println("Final PayloadLength: " + payloadLength);

            // Handle close frame immediately
            if (opcode == 8) { // Close frame
                sendCloseFrame(out);
                break;
            }

            // Only process payload for data frames (text or binary)
            if (opcode == 1 || opcode == 2) {
                // Read masking key
                byte[] maskingKey = null;
                if (masked) {
                    headerLength = readFully(in, buffer, offset, 4);
                    if (headerLength < 4) break;
                    maskingKey = Arrays.copyOfRange(buffer, offset, offset + 4);
                    System.out.println("Masking Key: " + Arrays.toString(maskingKey));
                    offset += 4;
                }

                // Skip processing if there's no payload
                if (payloadLength == 0) continue;

                // Read and unmask the entire payload at once
                byte[] payload = new byte[payloadLength];
                int bytesRead = readFully(in, payload, 0, payloadLength);
                if (bytesRead < payloadLength) break;

                System.out.println("Raw payload (first few bytes): " +
                        Arrays.toString(Arrays.copyOfRange(payload, 0, Math.min(10, payload.length))));

                // Unmask the entire payload
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ maskingKey[i & 0x3]);
                    }
                }

                // Now work with the unmasked payload
                if (payload.length > 0) {
                    int pathLength = payload[0] & 0xFF;
                    System.out.println("Decoded path length: " + pathLength);

                    if (pathLength > payload.length - 1) {
                        System.err.println("Invalid path length: " + pathLength +
                                " (payload length: " + payload.length + ")");
                        break;
                    }

                    // Extract path
                    byte[] pathBytes = Arrays.copyOfRange(payload, 1, pathLength + 1);
                    String path = new String(pathBytes, StandardCharsets.US_ASCII);
                    System.out.println("Decoded path: " + path);

                    // Extract message
                    byte[] messagePayload = Arrays.copyOfRange(payload, pathLength + 1, payload.length);
                    System.out.println("Message payload length: " + messagePayload.length);

                    SocketMessageHandler handler = getHandler(path);
                    if (handler != null) {
                        handler.handleMessage(messagePayload, sender);
                    } else {
                        System.err.println("No handler found for path: " + path + " keys:" + messageHandlers.keySet());
                    }
                }
            }
            // Other control frames (ping/pong) could be handled here
        }
    }

    protected SocketMessageHandler getHandler(String path) {
        return messageHandlers.get(path);
    }

    private void sendCloseFrame(OutputStream out) throws IOException {
        out.write(new byte[]{(byte) 0x88, 0x00}); // Close frame with empty payload
        out.flush();
    }

    private static int readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < length) {
            int bytesRead = in.read(buffer, offset + totalBytesRead, length - totalBytesRead);
            if (bytesRead == -1) break;
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    public void stop() {
        try {
            if (server != null && !server.isClosed()) {
                server.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Can't stop WebSocket Server", e);
        }
    }

    public static class WebSocketClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private static final SecureRandom random = new SecureRandom();

        public WebSocketClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(5000);
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        public void performHandshake() throws IOException {
            String key = "dGhlIHNhbXBsZSBub25jZQ=="; // In practice, generate this randomly
            String handshakeRequest =
                    "GET / HTTP/1.1\r\n" +
                            "Host: localhost:8081\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Key: " + key + "\r\n" +
                            "Sec-WebSocket-Version: 13\r\n\r\n";

            out.write(handshakeRequest.getBytes("UTF-8"));
            out.flush();

            // Read handshake response
            byte[] responseBuffer = new byte[1024];
            int responseBytes = in.read(responseBuffer);
            String response = new String(responseBuffer, 0, responseBytes, "UTF-8");
            System.out.println("Handshake response: " + response);
        }

        public void sendMessage(String path, String message) throws IOException {
            byte[] pathBytes = path.getBytes(StandardCharsets.US_ASCII);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[4];
            random.nextBytes(mask);

            // Calculate total payload length (1 byte for path length + path + message)
            int totalLength = 1 + pathBytes.length + messageBytes.length;

            System.out.println("Sending - Path: " + path + ", Path length: " + pathBytes.length);
            System.out.println("Total payload length: " + totalLength);
            System.out.println("Mask: " + Arrays.toString(mask));

            // Write frame header
            out.write(0x81); // FIN bit set, text frame
            out.write(0x80 | totalLength); // Mask bit set, payload length

            // Write mask
            out.write(mask);

            // Create complete payload first
            byte[] fullPayload = new byte[totalLength];
            fullPayload[0] = (byte) pathBytes.length;
            System.arraycopy(pathBytes, 0, fullPayload, 1, pathBytes.length);
            System.arraycopy(messageBytes, 0, fullPayload, 1 + pathBytes.length, messageBytes.length);

            // Mask the entire payload
            for (int i = 0; i < fullPayload.length; i++) {
                out.write(fullPayload[i] ^ mask[i & 0x3]);
            }

            out.flush();
        }

        public String receiveMessage() throws IOException {
            // Read frame header
            int byte1 = in.read();
            if (byte1 == -1) return null;

            int byte2 = in.read();
            if (byte2 == -1) return null;

            int payloadLength = byte2 & 0x7F;

            // Handle extended payload length
            if (payloadLength == 126) {
                byte[] extendedLength = new byte[2];
                readFully(in, extendedLength, 0, 2);
                payloadLength = ((extendedLength[0] & 0xFF) << 8) | (extendedLength[1] & 0xFF);
            } else if (payloadLength == 127) {
                byte[] extendedLength = new byte[8];
                readFully(in, extendedLength, 0, 8);
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength |= (extendedLength[i] & 0xFF) << ((7 - i) * 8);
                }
            }

            byte[] payload = new byte[payloadLength];
            int bytesRead = readFully(in, payload, 0, payloadLength);
            if (bytesRead < payloadLength) return null;

            return new String(payload, 0, bytesRead, "UTF-8");
        }

        public void sendClose() throws IOException {
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            out.write(new byte[]{(byte) 0x88, (byte) 0x80, mask[0], mask[1], mask[2], mask[3]});
            out.flush();
        }

        @Override
        public void close() throws IOException {
            sendClose();
            socket.close();
        }
    }
}
