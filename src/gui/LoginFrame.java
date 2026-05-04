package src.gui;

import src.client.ServerConnection;
import src.shared.Protocol;

import javax.swing.*;
import java.awt.*;

/**
 * Login window. Entry point for the Swing application.
 * On successful login opens the appropriate MainWindow (Doctor or Nurse).
 *
 * Usage: java src.gui.LoginFrame
 */
public class LoginFrame extends JFrame {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private static final Color CLR_BG = new Color(173, 216, 230);
    private static final Color CLR_FIELD = new Color(210, 230, 245);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 16);
    private static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 13);

    private JTextField userIdField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel statusLabel;

    public LoginFrame() {
        setTitle("Hospital Management System — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 240);
        setLocationRelativeTo(null);
        setResizable(false);
        buildUI();
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(CLR_BG);
        main.setBorder(BorderFactory.createEmptyBorder(24, 36, 20, 36));

        // Title
        JLabel title = new JLabel("🏥  Hospital Management System", SwingConstants.CENTER);
        title.setFont(FONT_TITLE);
        title.setForeground(new Color(30, 70, 110));
        main.add(title, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 10));
        form.setOpaque(false);

        form.add(makeLabel("User ID:"));
        userIdField = makeTextField();
        form.add(userIdField);

        form.add(makeLabel("Password:"));
        passwordField = new JPasswordField();
        styleField(passwordField);
        form.add(passwordField);

        main.add(form, BorderLayout.CENTER);

        // Bottom: button + status
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

        // Allow Enter key from password field
        passwordField.addActionListener(e -> performLogin());

        setContentPane(main);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Login logic
    // ─────────────────────────────────────────────────────────────────────

    private void performLogin() {
        String userId = userIdField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (userId.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter User ID and password.");
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setForeground(new Color(60, 90, 130));
        statusLabel.setText("Connecting…");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            private ServerConnection conn;

            @Override
            protected String doInBackground() throws Exception {
                conn = new ServerConnection(SERVER_HOST, SERVER_PORT);
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
                        // Wire up real-time push: server tells all clients to refresh
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

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_LABEL);
        return l;
    }

    private JTextField makeTextField() {
        JTextField tf = new JTextField();
        styleField(tf);
        return tf;
    }

    private void styleField(JTextField tf) {
        tf.setBackground(CLR_FIELD);
        tf.setFont(FONT_LABEL);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(140, 180, 210)),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}