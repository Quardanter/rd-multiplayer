package server.commands;

import server.Server;
import server.client.Client;

import java.util.Set;

public class ListCommand implements Command {
    @Override
    public String name() {
        return "list";
    }

    @Override
    public void execute(String[] args) {

        Set<Client> clients = Server.clients;

        System.out.print("Online Players: ");
        for (Client client : clients) {
            String username = client.getUsername();
            System.out.printf("%s ", username);
        }
        System.out.printf("%n");
    }
}
