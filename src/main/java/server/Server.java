package server;

import server.client.Client;
import server.client.ClientHandler;
import server.client.TimeoutHandler;
import server.level.Level;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    public static Level level;
    public static final Set<Client> clients = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<Client, Long> lastKeepAlive = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        level = new Level(256, 256, 64);
        level.save();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving level...");
            level.save();
        }));

        int port = 9090;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("Port must be between 1 and 65535");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);

        TimeoutHandler.start();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected from: " + clientSocket.getInetAddress().getHostAddress());
            new Thread(() -> ClientHandler.handle(clientSocket)).start();
        }
    }
}