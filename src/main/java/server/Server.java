package server;

import server.client.Client;
import server.client.ClientHandler;
import server.client.TimeoutHandler;
import server.level.Level;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Path PROPERTIES_PATH = Paths.get("server.properties");

    public static int PORT         = 9090;
    public static int PLAYER_LIMIT = 50;
    public static int MAX_PER_IP   = 3;

    public static Level level;

    public static final Set<Client>                   clients       = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<Client,Long> lastKeepAlive = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        loadProperties();

        level = new Level(64);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving all chunks...");
            level.save();
        }));

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        TimeoutHandler.start();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected from: "
                    + clientSocket.getInetAddress().getHostAddress());
            new Thread(() -> ClientHandler.handle(clientSocket)).start();
        }
    }

    private static void loadProperties() {
        try {
            if (!Files.exists(PROPERTIES_PATH)) createDefaultProperties();

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(PROPERTIES_PATH)) { p.load(in); }

            PORT         = Integer.parseInt(p.getProperty("port",         "9090"));
            PLAYER_LIMIT = Integer.parseInt(p.getProperty("player_limit", "50"));
            MAX_PER_IP   = Integer.parseInt(p.getProperty("max_per_ip",   "3"));

            System.out.println("Loaded server.properties");
        } catch (Exception e) {
            System.err.println("Failed to load server.properties");
            e.printStackTrace();
        }
    }

    private static void createDefaultProperties() throws IOException {
        Properties d = new Properties();
        d.setProperty("port",         "9090");
        d.setProperty("player_limit", "50");
        d.setProperty("max_per_ip",   "3");
        try (OutputStream out = Files.newOutputStream(PROPERTIES_PATH)) {
            d.store(out, "Server Properties");
        }
        System.out.println("Created default server.properties");
    }
}