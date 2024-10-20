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
                        byte[] decoded = new byte[6];
                        byte[] encoded = new byte[6];
                        byte[] key = new byte[4];
                        in.read(encoded, 0, encoded.length);
                        in.read(key, 0, key.length);
                        for (int i = 0; i < encoded.length; i++) {
                            decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
                        }
                        System.out.println("Decoded message: " + new String(decoded));
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
    }

    public void stop() {
        try {
            if (server != null && !server.isClosed()) {
                server.close();
                System.out.println("WebSocket server stopped.");
            }
        } catch (IOException e) {
            throw new TinyWeb.ServerException("Can't stop WebSocket Server", e);
        }
    }
}
