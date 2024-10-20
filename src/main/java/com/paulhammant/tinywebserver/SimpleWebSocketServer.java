package com.paulhammant.tinywebserver;

import java.io.IOException;
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
                            Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                            if (!match.find()) {
                                client.close();
                                continue;
                            }

                            // Handle handshake
                            String acceptKey = Base64.getEncoder().encodeToString(
                                    MessageDigest.getInstance("SHA-1")
                                            .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                                    .getBytes("UTF-8")));

                            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                    + "Connection: Upgrade\r\n"
                                    + "Upgrade: websocket\r\n"
                                    + "Sec-WebSocket-Accept: " + acceptKey
                                    + "\r\n\r\n").getBytes("UTF-8");

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
                                if (bytesRead < payloadLength) break;

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
            if (bytesRead == -1) break; // Stream ended
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
}