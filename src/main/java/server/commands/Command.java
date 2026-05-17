package server.commands;

public interface Command {
    String name();
    void execute(String[] args);
}
