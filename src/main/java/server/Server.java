package server;

import server.client.Client;
import server.client.ClientHandler;
import server.client.TimeoutHandler;
import server.level.Level;
import server.net.Broadcaster;

import java.util.*;
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
    public static final Path BANNED_PATH = Paths.get("banned_ips.json");

    public static int PORT         = 9090;
    public static int PLAYER_LIMIT = 50;
    public static int MAX_PER_IP   = 3;

    public static boolean ANTICHEAT      = true;
    public static double  MAX_REACH      = 10.0;
    public static double  MOVE_RATE      = 20.0;   // moves / sec
    public static double  MOVE_BURST     = 10.0;
    public static double  PLACE_RATE     = 5.0;    // places / sec
    public static double  BREAK_RATE     = 5.0;    // breaks / sec
    public static int     RENDER_DISTANCE = 8;

    public static Level level;

    public static final Set<Client>                   clients       = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<Client,Long> lastKeepAlive = new ConcurrentHashMap<>();

    /** username → raw PNG bytes of their uploaded skin (if any). */
    public static final ConcurrentHashMap<String, byte[]> skins = new ConcurrentHashMap<>();

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

        Scanner scanner = new Scanner(System.in);
        Thread t = new Thread(() -> {
            while (scanner.hasNextLine()) {
                String command = scanner.nextLine();
                String[] parts = command.split(" ", 2);

                switch (parts[0]) {
                    case "kick":
                        String username = parts[1];
                        Optional<Client> found = clients.stream()
                                .filter(c -> c.getUsername().equals(username))
                                .findFirst();

                        if (found.isPresent()) {
                            found.get().close();
                            System.out.println("Kicked " + username);
                        } else {
                            System.out.println("Player not found: " + username);
                        }
                        break;
                    case "say":
                        String message = parts[1];
                        Broadcaster.broadcastChat("SERVER", message);
                }
            }
        });
        t.start();


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
            if (!Files.exists(BANNED_PATH)) createBannedJSON();

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(PROPERTIES_PATH)) { p.load(in); }

            PORT         = Integer.parseInt(p.getProperty("port",         "9090"));
            PLAYER_LIMIT = Integer.parseInt(p.getProperty("player_limit", "50"));
            MAX_PER_IP   = Integer.parseInt(p.getProperty("max_per_ip",   "3"));

            ANTICHEAT       = Boolean.parseBoolean(p.getProperty("anticheat",       "true"));
            MAX_REACH       = Double.parseDouble (p.getProperty("max_reach",        "10.0"));
            MOVE_RATE       = Double.parseDouble (p.getProperty("move_rate",        "20.0"));
            MOVE_BURST      = Double.parseDouble (p.getProperty("move_burst",       "10.0"));
            PLACE_RATE      = Double.parseDouble (p.getProperty("place_rate",       "5.0"));
            BREAK_RATE      = Double.parseDouble (p.getProperty("break_rate",       "5.0"));
            RENDER_DISTANCE = Integer.parseInt   (p.getProperty("render_distance",  "8"));

            System.out.println("Loaded server.properties");
        } catch (Exception e) {
            System.err.println("Failed to load server.properties");
            e.printStackTrace();
        }
    }

    private static void createDefaultProperties() throws IOException {
        Properties d = new Properties();
        d.setProperty("port",            "9090");
        d.setProperty("player_limit",    "50");
        d.setProperty("max_per_ip",      "3");
        d.setProperty("anticheat",       "true");
        d.setProperty("max_reach",       "10.0");
        d.setProperty("move_rate",       "20.0");
        d.setProperty("move_burst",      "10.0");
        d.setProperty("place_rate",      "5.0");
        d.setProperty("break_rate",      "5.0");
        d.setProperty("render_distance", "8");
        try (OutputStream out = Files.newOutputStream(PROPERTIES_PATH)) {
            d.store(out, "Server Properties");
        }
        System.out.println("Created default server.properties");
    }

    private static void createBannedJSON() throws IOException {
        try (FileWriter writer = new FileWriter(BANNED_PATH.toFile())) {
            writer.write("[]");
        }
    }
}