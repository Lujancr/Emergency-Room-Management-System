package src.gui;

import src.client.ServerConnection;
import src.shared.Protocol;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {

    // Default port must match HospitalServer.DEFAULT_PORT (2620)
    private static final int DEFAULT_PORT = 2620;
    // IP Address must match the server's IP or 'localhost'
    private static final String DEFAULT_HOST = "localhost";

    // Custom colors and fonts for styling the login UI
    private static final Color CLR_BG = new Color(173, 216, 230);
    private static final Color CLR_FIELD = new Color(210, 230, 245);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 16);
    private static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 13);

    // UI components for the login form
    private JTextField serverIpField;
    private JTextField userIdField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel statusLabel;

    // Constructor method that sets up the login frame
    public LoginFrame() {
        setTitle("Hospital Management System — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 290);
        setLocationRelativeTo(null);
        setResizable(false);
        buildUI();
    }

    // Method that creates the UI components and arranges them in the frame
    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(CLR_BG);
        main.setBorder(BorderFactory.createEmptyBorder(24, 36, 20, 36));

        JLabel title = new JLabel("🏥  Hospital Management System", SwingConstants.CENTER);
        title.setFont(FONT_TITLE);
        title.setForeground(new Color(30, 70, 110));
        main.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 10));
        form.setOpaque(false);

        form.add(makeLabel("Server IP:"));
        serverIpField = makeTextField();
        serverIpField.setText(DEFAULT_HOST);
        form.add(serverIpField);

        form.add(makeLabel("User ID:"));
        userIdField = makeTextField();
        form.add(userIdField);

        form.add(makeLabel("Password:"));
        passwordField = new JPasswordField();
        styleField(passwordField);
        form.add(passwordField);

        main.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(5, 6));
        bottom.setOpaque(false);

        loginButton = new JButton("Login");
        loginButton.setFont(FONT_LABEL);
        loginButton.setBackground(new Color(210, 210, 210));
        loginButton.setFocusPainted(false);
        loginButton.addActionListener(e -> performLogin());

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLabel.setForeground(Color.RED);

        bottom.add(loginButton, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        main.add(bottom, BorderLayout.SOUTH);

        passwordField.addActionListener(e -> performLogin());

        setContentPane(main);
    }

    // Method that handles the login process
    private void performLogin() {
        String serverIp = serverIpField.getText().trim();
        String userId = userIdField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (serverIp.isEmpty()) {
            statusLabel.setText("Please enter a server IP address.");
            return;
        }
        if (userId.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter User ID and password.");
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setForeground(new Color(60, 90, 130));
        statusLabel.setText("Connecting to " + serverIp + "…");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            private ServerConnection conn;

            @Override
            protected String doInBackground() throws Exception {
                conn = new ServerConnection(serverIp, DEFAULT_PORT);
                conn.connect();
                boolean ok = conn.login(userId, password);
                if (!ok) {
                    conn.disconnect();
                    return null;
                }
                return conn.getRole();
            }

            @Override
            protected void done() {
                try {
                    String role = get();
                    if (role == null) {
                        statusLabel.setForeground(Color.RED);
                        statusLabel.setText("Invalid credentials.");
                        loginButton.setEnabled(true);
                    } else {
                        dispose();
                        boolean isDoctor = Protocol.ROLE_DOCTOR.equals(role);
                        MainWindow mainWindow = new MainWindow(conn, isDoctor);
                        conn.setOnPushRefresh(mainWindow::refreshPatientList);
                        mainWindow.setVisible(true);
                    }
                } catch (Exception ex) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Connection failed: " + ex.getMessage());
                    loginButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // Method to create a label
    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_LABEL);
        return l;
    }

    // Method to create a text field
    private JTextField makeTextField() {
        JTextField tf = new JTextField();
        styleField(tf);
        return tf;
    }

    // Method to apply consistent styling to text fields
    private void styleField(JTextField tf) {
        tf.setBackground(CLR_FIELD);
        tf.setFont(FONT_LABEL);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(140, 180, 210)),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
    }

    // Main method to launch the program and display the login frame
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
