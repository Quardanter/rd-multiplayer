package server.commands;

import server.Server;
import server.client.Client;

import java.util.Optional;

public class KickCommand implements Command {
    @Override
    public String name() {
        return "kick";
    }

    @Override
    public void execute(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: kick <player>");
            return;
        }

        String username = args[0];

        Optional<Client> client = Server.getClient(username);

        if (client.isPresent()) {
            client.get().close();
            System.out.printf("Kicked %s%n", username);
        } else {
            System.out.println("Player not found.");
        }
    }
}
