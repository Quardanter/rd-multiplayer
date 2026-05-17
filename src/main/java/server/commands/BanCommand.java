package server.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import server.Server;
import server.client.Client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

public class BanCommand implements Command {
    @Override
    public String name() {
        return "ban";
    }

    @Override
    public void execute(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: ban <player>");
            return;
        }

        String username = args[0];
        Optional<Client> client = Server.getClient(username);

        client.ifPresent(c -> {
            try {
                String ip = c.getSocket().getInetAddress().getHostAddress();

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                File file = new File("banned_ips.json");
                JsonArray bannedIPS;
                if (file.exists() && file.length() > 0) {
                    bannedIPS = JsonParser.parseReader(new FileReader(file)).getAsJsonArray();
                } else {
                    bannedIPS = new JsonArray();
                }

                bannedIPS.add(ip);
                c.close();
                System.out.printf("Banned %s%n", username);
                Files.write(file.toPath(), gson.toJson(bannedIPS).getBytes());
            } catch (IOException ignored) {}
        });
    }
}
