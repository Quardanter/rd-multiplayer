package client;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.IOException;

public class Launcher {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Launcher::showLauncher);
    }

    private static void showLauncher() {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(300, 200);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.decode("#141414"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("rd-multiplayer launcher", SwingConstants.CENTER);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.weightx = 1.0;
        frame.add(titleLabel, c);

        JLabel ipLabel = new JLabel("IP:");
        ipLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; c.weightx = 0.2;
        frame.add(ipLabel, c);

        JTextField ipField = new JTextField("localhost");
        ipField.setBackground(Color.decode("#141414"));
        ipField.setForeground(Color.WHITE);
        ipField.setCaretColor(Color.WHITE);
        ipField.setBorder(new LineBorder(Color.WHITE, 1));
        c.gridx = 1; c.gridy = 1; c.weightx = 0.8;
        frame.add(ipField, c);

        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = 2; c.weightx = 0.2;
        frame.add(portLabel, c);

        JTextField portField = new JTextField("9090");
        portField.setBackground(Color.decode("#141414"));
        portField.setForeground(Color.WHITE);
        portField.setCaretColor(Color.WHITE);
        portField.setBorder(new LineBorder(Color.WHITE, 1));
        c.gridx = 1; c.gridy = 2; c.weightx = 0.8;
        frame.add(portField, c);

        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = 3; c.weightx = 0.2;
        frame.add(usernameLabel, c);

        JTextField usernameField = new JTextField("Player");
        usernameField.setBackground(Color.decode("#141414"));
        usernameField.setForeground(Color.WHITE);
        usernameField.setCaretColor(Color.WHITE);
        usernameField.setBorder(new LineBorder(Color.WHITE, 1));
        c.gridx = 1; c.gridy = 3; c.weightx = 0.8;
        frame.add(usernameField, c);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(Color.decode("#141414"));

        JButton connectButton = new JButton("Connect");
        connectButton.setBackground(Color.decode("#141414"));
        connectButton.setForeground(Color.WHITE);
        connectButton.setBorder(new LineBorder(Color.WHITE, 1));

        JButton closeButton = new JButton("Close");
        closeButton.setBackground(Color.decode("#141414"));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBorder(new LineBorder(Color.WHITE, 1));
        closeButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(connectButton);
        buttonPanel.add(closeButton);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1.0;
        frame.add(buttonPanel, c);

        connectButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid port number");
                return;
            }
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Username cannot be empty");
                return;
            }

            frame.dispose();
            startGame(ip, port, username);
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void startGame(String ip, int port, String username) {
        try {
            Minecraft mc = new Minecraft(ip, port, username);
            new Thread(mc).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to start game: " + e.getMessage());
        }
    }
}
