package server.commands;

import server.net.Broadcaster;


public class SayCommand implements Command {
    @Override
    public String name() {
        return "say";
    }

    @Override
    public void execute(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: say <message>");
            return;
        }

        String message = args[0];
        Broadcaster.broadcastChat("SERVER", message);
    }
}
