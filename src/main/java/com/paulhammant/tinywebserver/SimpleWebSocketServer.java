package com.paulhammant.tinywebserver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleWebSocketServer {
    private final int port;
    private ServerSocket server;
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds timeout

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = new ServerSocket(port);
            System.out.println("WebSocket server started on port " + port);

            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    client.setSoTimeout(SOCKET_TIMEOUT); // Add timeout to prevent infinite waiting
                    System.out.println("A client connected.");

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
                                continue;
                            }

                            // Handle handshake
                            System.out.println("Sec-WebSocket-Key found: " + match.group(1));
                            String acceptKey = Base64.getEncoder().encodeToString(
                                    MessageDigest.getInstance("SHA-1")
                                            .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                                    .getBytes("UTF-8")));
                            System.out.println("Computed Sec-WebSocket-Accept: " + acceptKey);

                            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                    + "Connection: Upgrade\r\n"
                                    + "Upgrade: websocket\r\n"
                                    + "Sec-WebSocket-Accept: " + acceptKey
                                    + "\r\n\r\n").getBytes("UTF-8");

                            System.out.println("Sending handshake response: " + new String(response, "UTF-8"));
                            out.write(response);
                            out.flush();

                            // Handle messages with proper buffer reading
                            byte[] buffer = new byte[8192]; // Use a buffer for reading
                            while (!client.isClosed()) {
                                // Read the header bytes into the buffer
                                int headerBytes = readFully(in, buffer, 0, 2);
                                if (headerBytes < 2) break; // Connection closed or error

                                int payloadLength = buffer[1] & 0x7F;
                                int totalLength = 2; // Start with 2 header bytes

                                // Handle extended payload length
                                if (payloadLength == 126) {
                                    headerBytes = readFully(in, buffer, totalLength, 2);
                                    if (headerBytes < 2) break;
                                    payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                                    totalLength += 2;
                                } else if (payloadLength == 127) {
                                    headerBytes = readFully(in, buffer, totalLength, 8);
                                    if (headerBytes < 8) break;
                                    payloadLength = 0;
                                    for (int i = 0; i < 8; i++) {
                                        payloadLength |= (buffer[totalLength + i] & 0xFF) << ((7 - i) * 8);
                                    }
                                    totalLength += 8;
                                }

                                // Read masking key
                                headerBytes = readFully(in, buffer, totalLength, 4);
                                if (headerBytes < 4) break;
                                byte[] maskingKey = Arrays.copyOfRange(buffer, totalLength, totalLength + 4);
                                totalLength += 4;

                                // Read payload
                                byte[] payload = new byte[payloadLength];
                                int bytesRead = readFully(in, payload, 0, payloadLength);
                                if (bytesRead < payloadLength) {
                                    System.out.println("Error: Expected " + payloadLength + " bytes, but read " + bytesRead);
                                    break;
                                }

                                // Unmask the payload
                                for (int i = 0; i < payloadLength; i++) {
                                    payload[i] = (byte) (payload[i] ^ maskingKey[i & 0x3]);
                                }

                                // Echo back
                                out.write(0x81); // Text frame
                                if (payloadLength < 126) {
                                    out.write(payloadLength);
                                } else if (payloadLength <= 65535) {
                                    out.write(126);
                                    out.write((payloadLength >> 8) & 0xFF);
                                    out.write(payloadLength & 0xFF);
                                } else {
                                    out.write(127);
                                    for (int i = 7; i >= 0; i--) {
                                        out.write((payloadLength >> (8 * i)) & 0xFF);
                                    }
                                }
                                out.write(payload);
                                out.flush();
                            }
                        }
                    } finally {
                        s.close();
                        client.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                    // Continue serving other clients
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new TinyWeb.ServerException("Can't start WebSocket Server", e);
        }
    }

    // Helper method to ensure we read the exact number of bytes needed
    private int readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < length) {
            int bytesRead = in.read(buffer, offset + totalBytesRead, length - totalBytesRead);
            if (bytesRead == -1) {
                System.out.println("Stream ended or connection closed unexpectedly.");
                break;
            }
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
            throw new TinyWeb.ServerException("Can't stop WebSocket Server", e);
        }
    }
    public static void main(String[] args) {
        SimpleWebSocketServer server = new SimpleWebSocketServer(8081);
        new Thread(server::start).start();

        long start = System.currentTimeMillis();

        try (Socket socket = new Socket("localhost", 8081)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Perform WebSocket handshake
            String handshakeRequest = "GET / HTTP/1.1\r\n"
                    + "Host: localhost:8081\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(handshakeRequest.getBytes("UTF-8"));
            out.flush();

            // Read handshake response
            byte[] responseBuffer = new byte[1024];
            int responseBytes = in.read(responseBuffer);
            String response = new String(responseBuffer, 0, responseBytes, "UTF-8");
            System.out.println("Handshake response: " + response);

            // Send a WebSocket text frame
            String messageToSend = "Hello WebSocket";
            out.write(0x81); // FIN bit set and text frame
            out.write(messageToSend.length());
            out.write(messageToSend.getBytes("UTF-8"));
            out.flush();

            // Read the echoed message
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);
            String receivedMessage = null;
            try {
                receivedMessage = new String(buffer, 0, bytesRead, "UTF-8");
            } catch (Exception e) {
                System.out.println("1. Exception " +(System.currentTimeMillis() - start) + " millis later" + e.getMessage());


//                Error encountered
//                ==========================
//
//                WebSocket server started on port 8081
//                A client connected.
//                        Received handshake data: GET / HTTP/1.1
//                Host: localhost:8081
//                Upgrade: websocket
//                Connection: Upgrade
//                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
//                        Sec-WebSocket-Version: 13
//                Sec-WebSocket-Key found: dGhlIHNhbXBsZSBub25jZQ==
//                        Computed Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
//                        Sending handshake response: HTTP/1.1 101 Switching Protocols
//                Connection: Upgrade
//                Upgrade: websocket
//                Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
//
//
//                        Handshake response: HTTP/1.1 101 Switching Protocols
//                Connection: Upgrade
//                Upgrade: websocket
//                Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
//
//
//                        Error handling client: Read timed out
//                java.lang.RuntimeException: java.lang.StringIndexOutOfBoundsException: Range [0, 0 + -1) out of bounds for length 1024
//                at com.paulhammant.tinywebserver.SimpleWebSocketServer.main(SimpleWebSocketServer.java:212)
//                Caused by: java.lang.StringIndexOutOfBoundsException: Range [0, 0 + -1) out of bounds for length 1024
//                at java.base/jdk.internal.util.Preconditions$1.apply(Preconditions.java:55)
//                at java.base/jdk.internal.util.Preconditions$1.apply(Preconditions.java:52)
//                at java.base/jdk.internal.util.Preconditions$4.apply(Preconditions.java:213)
//                at java.base/jdk.internal.util.Preconditions$4.apply(Preconditions.java:210)
//                at java.base/jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:98)
//                at java.base/jdk.internal.util.Preconditions.outOfBoundsCheckFromIndexSize(Preconditions.java:118)
//                at java.base/jdk.internal.util.Preconditions.checkFromIndexSize(Preconditions.java:397)
//                at java.base/java.lang.String.checkBoundsOffCount(String.java:4853)
//                at java.base/java.lang.String.<init>(String.java:488)
//                at com.paulhammant.tinywebserver.SimpleWebSocketServer.main(SimpleWebSocketServer.java:209)
//                Error handling client: Socket closed
//                1. Exception 30089 millis laterRange [0, 0 + -1) out of bounds for length 1024
//                2. Exception 30092 millis later - java.lang.StringIndexOutOfBoundsException: Range [0, 0 + -1) out of bounds for length 1024
//
            }

            // Assert the echoed message is correct
            if (!receivedMessage.contains(messageToSend)) {
                throw new AssertionError("Expected: " + messageToSend + " but was: " + receivedMessage);
            } else {
                System.out.println("Test passed: Echoed message is correct.");
            }
        } catch (Exception e) {
            System.out.println("2. Exception " +(System.currentTimeMillis() - start) + " millis later - " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}
