package com.paulhammant.tinywebserver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;
public class SimpleWebSocketServer {

    @FunctionalInterface
    public interface WebSocketMessageHandler {
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
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds timeout
    private static final SecureRandom random = new SecureRandom();
    private WebSocketMessageHandler messageHandler;

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    public void registerMessageHandler(WebSocketMessageHandler handler) {
        this.messageHandler = handler;
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

                    new Thread(() -> handleClient(client)).start();
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

            // Handle extended payload length
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

            // Read masking key if present
            byte[] maskingKey = null;
            if (masked) {
                headerLength = readFully(in, buffer, offset, 4);
                if (headerLength < 4) break;
                maskingKey = Arrays.copyOfRange(buffer, offset, offset + 4);
                offset += 4;
            }

            // Read payload
            byte[] payload = new byte[payloadLength];
            int bytesRead = readFully(in, payload, 0, payloadLength);
            if (bytesRead < payloadLength) break;

            // Unmask if necessary
            if (masked) {
                for (int i = 0; i < payloadLength; i++) {
                    payload[i] = (byte) (payload[i] ^ maskingKey[i & 0x3]);
                }
            }

            // Handle the frame
            if (opcode == 8) { // Close frame
                sendCloseFrame(out);
                break;
            } else if (opcode == 1) { // Text frame
                messageHandler.handleMessage(payload, sender);
            }
        }
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

    public static void main(String[] args) {
        SimpleWebSocketServer server = new SimpleWebSocketServer(8081);

//        // Example of registering a custom handler
//        server.registerMessageHandler((message, sender) -> {
//            String receivedText = new String(message, "UTF-8");
//            String response = "Received: " + receivedText;
//            sender.sendTextFrame(response.getBytes("UTF-8"));
//        });

        server.registerMessageHandler((message, sender) -> {
            for (int i = 1; i <= 3; i++) {
                String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread serverThread = new Thread(server::start);
        serverThread.start();

        // Give the server a moment to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        try (Socket socket = new Socket("localhost", 8081)) {
            socket.setSoTimeout(5000); // 5 second timeout
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Perform WebSocket handshake
            String handshakeRequest =
                    "GET / HTTP/1.1\r\n" +
                            "Host: localhost:8081\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                            "Sec-WebSocket-Version: 13\r\n\r\n";

            out.write(handshakeRequest.getBytes("UTF-8"));
            out.flush();

            // Read handshake response
            byte[] responseBuffer = new byte[1024];
            int responseBytes = in.read(responseBuffer);
            String response = new String(responseBuffer, 0, responseBytes, "UTF-8");
            System.out.println("Handshake response: " + response);

            // Send a masked WebSocket text frame
            String messageToSend = "Hello WebSocket";
            byte[] messageBytes = messageToSend.getBytes("UTF-8");

            // Generate random mask
            byte[] mask = new byte[4];
            random.nextBytes(mask);

            // Write frame header
            out.write(0x81); // FIN bit set, text frame
            out.write(0x80 | messageBytes.length); // Mask bit set, payload length
            out.write(mask); // Write mask

            // Write masked payload
            for (int i = 0; i < messageBytes.length; i++) {
                out.write(messageBytes[i] ^ mask[i % 4]);
            }
            out.flush();

            // Read response frame header
            int byte1 = in.read(); // FIN and opcode
            int byte2 = in.read(); // Mask and payload length
            int payloadLength = byte2 & 0x7F;

            // Read payload
            byte[] payload = new byte[payloadLength];
            int bytesRead = readFully(in, payload, 0, payloadLength);
            String receivedMessage = new String(payload, 0, bytesRead, "UTF-8");

            System.out.println("Received message from server: " + receivedMessage);

            // Send close frame
            out.write(new byte[]{(byte) 0x88, (byte) 0x80, mask[0], mask[1], mask[2], mask[3]});
            out.flush();

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }



}