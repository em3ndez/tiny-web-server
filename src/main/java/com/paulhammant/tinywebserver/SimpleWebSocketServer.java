package com.paulhammant.tinywebserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleWebSocketServer {

    private final int port;

    private ServerSocket server;

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = new ServerSocket(port);
            System.out.println("WebSocket server started on port " + port);

            while (true) {
                Socket client = server.accept();
                System.out.println("A client connected.");

                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                Scanner s = new Scanner(in, "UTF-8");

                try {
                    String data = s.useDelimiter("\\r\\n\\r\\n").next();
                    Matcher get = Pattern.compile("^GET").matcher(data);

                    if (get.find()) {
                        Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                        match.find();
                        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                + "Connection: Upgrade\r\n"
                                + "Upgrade: websocket\r\n"
                                + "Sec-WebSocket-Accept: "
                                + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                                .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
                                + "\r\n\r\n").getBytes("UTF-8");
                        out.write(response, 0, response.length);

                        // Handle messages
                        while (true) {
                            System.out.println("Waiting to read first byte of message...");
                            int firstByte = in.read();
                            System.out.println("First byte read: " + firstByte);
                            if (firstByte == -1) break; // End of stream

                            System.out.println("Waiting to read second byte of message...");
                            int secondByte = in.read();
                            System.out.println("Second byte read: " + secondByte);
                            int payloadLength = secondByte & 0x7F;

                            if (payloadLength == 126) {
                                payloadLength = in.read() << 8 | in.read();
                            } else if (payloadLength == 127) {
                                payloadLength = (int) (in.read() << 56 | in.read() << 48 | in.read() << 40 | in.read() << 32 |
                                        in.read() << 24 | in.read() << 16 | in.read() << 8 | in.read());
                            }

                            System.out.println("Payload length: " + payloadLength);
                            byte[] key = new byte[4];
                            System.out.println("Reading masking key...");
                            in.read(key, 0, key.length);

                            System.out.println("Masking key read: " + Arrays.toString(key));
                            byte[] encoded = new byte[payloadLength];
                            System.out.println("Reading encoded message...");
                            in.read(encoded, 0, encoded.length);

                            System.out.println("Encoded message read: " + Arrays.toString(encoded));
                            byte[] decoded = new byte[payloadLength];
                            System.out.println("Decoding message...");
                            for (int i = 0; i < encoded.length; i++) {
                                decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
                            }

                            System.out.println("Decoded message: " + new String(decoded));
                            System.out.println("Echoing message back to client...");

                            // Echo the message back
                            out.write(0x81); // 0x81 indicates a text frame
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
                            out.write(decoded); // Send the decoded message back
                            out.flush();
                        }
                    }
                } finally {
                    s.close();
                    client.close();
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new TinyWeb.ServerException("Can't start WebSocket Server", e);
        }
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
