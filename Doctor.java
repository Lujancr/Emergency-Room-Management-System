import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class Doctor extends JFrame implements ActionListener {

    // Labels and textFields
    private final JLabel l2 = new JLabel("Name: ");
    private final JTextField nameField = new JTextField();

    // New code
    private final JLabel l3 = new JLabel("SSN: ");
    private final JTextField SSNField = new JTextField();
    private final JLabel l4 = new JLabel("Age: ");
    private final JTextField ageField = new JTextField();
    private final JLabel l5 = new JLabel("Height: ");
    private final JTextField heightField = new JTextField();
    private final JLabel l6 = new JLabel("Weight: ");
    private final JTextField weightField = new JTextField();
    private final JLabel l7 = new JLabel("Condition: ");
    private final JTextField conditionField = new JTextField();
    private final JLabel l8 = new JLabel("Severity: ");
    private final JTextField severityField = new JTextField();
    private final JLabel l9 = new JLabel("Notes: ");
    private final JTextField notesField = new JTextField();

    // Swing list
    private final DefaultListModel<String> invitedModel = new DefaultListModel<>();
    private final JList<String> invitedList = new JList<>(invitedModel);

    // Action buttons
    private final JButton createBtn = new JButton("Create Patient");
    private final JButton cancelBtn = new JButton("Clear");

    // Status label
    private final JLabel statusLabel = new JLabel("");

    // Constructor
    public Doctor() {
        final Color LIGHT_BLUE = new Color(51, 204, 255);
        final Color DARK_GREY = Color.DARK_GRAY;
        final Color WHITE = Color.WHITE;
        final Color BLACK = Color.BLACK;

        // new code
        l2.setBounds(20, 100, 80, 25);
        l2.setForeground(BLACK);
        add(l2);

        nameField.setBounds(105, 100, 250, 25);
        nameField.setBackground(WHITE);
        nameField.setForeground(BLACK);
        add(nameField);

        l3.setBounds(20, 130, 80, 25);
        l3.setForeground(BLACK);
        add(l3);

        SSNField.setBounds(105, 130, 250, 25);
        SSNField.setBackground(WHITE);
        SSNField.setForeground(BLACK);
        add(SSNField);

        l4.setBounds(20, 160, 80, 25);
        l4.setForeground(BLACK);
        add(l4);

        ageField.setBounds(105, 160, 250, 25);
        ageField.setBackground(WHITE);
        ageField.setForeground(BLACK);
        add(ageField);

        l5.setBounds(20, 190, 80, 25);
        l5.setForeground(BLACK);
        add(l5);

        heightField.setBounds(105, 190, 250, 25);
        heightField.setBackground(WHITE);
        heightField.setForeground(BLACK);
        add(heightField);

        l6.setBounds(20, 220, 80, 25);
        l6.setForeground(BLACK);
        add(l6);

        weightField.setBounds(105, 220, 250, 25);
        weightField.setBackground(WHITE);
        weightField.setForeground(BLACK);
        add(weightField);

        l7.setBounds(20, 250, 80, 25);
        l7.setForeground(BLACK);
        add(l7);

        conditionField.setBounds(105, 250, 250, 50);
        conditionField.setBackground(WHITE);
        conditionField.setForeground(BLACK);
        add(conditionField);

        l8.setBounds(20, 310, 80, 25);
        l8.setForeground(BLACK);
        add(l8);

        severityField.setBounds(105, 310, 50, 25);
        severityField.setBackground(WHITE);
        severityField.setForeground(BLACK);
        add(severityField);

        l9.setBounds(20, 340, 80, 25);
        l9.setForeground(BLACK);
        add(l9);

        notesField.setBounds(105, 340, 250, 50);
        notesField.setBackground(WHITE);
        notesField.setForeground(BLACK);
        add(notesField);

        // Basic window setup
        setLayout(null);
        getContentPane().setBackground(LIGHT_BLUE);

        setTitle("Doctor");
        createBtn.setText("Create Patient");

        setSize(450, 550);
        setLocationRelativeTo(null);

        // Heading
        JLabel heading = new JLabel("Create Patient Window 2");
        heading.setBounds(160, 15, 200, 25);
        heading.setForeground(BLACK);
        add(heading);

        // Create / Cancel buttons
        createBtn.setBounds(100, 400, 120, 35);
        createBtn.setBackground(Color.GRAY);
        createBtn.setForeground(BLACK);
        add(createBtn);

        cancelBtn.setBounds(240, 400, 120, 35);
        cancelBtn.setBackground(Color.GRAY);
        cancelBtn.setForeground(BLACK);
        add(cancelBtn);

        // Listeners
        createBtn.addActionListener(this);
        cancelBtn.addActionListener(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        // Handle button clicks
        if (src == createBtn) {
            // Their code goes here.
            
        } else if (src == cancelBtn) {
            // set the text to clear
            nameField.setText("");
            SSNField.setText("");
            ageField.setText("");
            heightField.setText("");
            weightField.setText("");
            conditionField.setText("");
            severityField.setText("");
        }
    }

    public static void main(String[] args) {
        new Doctor();
    }
}