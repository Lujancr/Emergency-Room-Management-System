package src.gui;

import src.client.ServerConnection;
import src.model.Patient;
import src.shared.Protocol;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

// The main window is the primary application frame shared by the doctor and nurse.
public class MainWindow extends JFrame {

    // ── Colors
    private static final Color CLR_HEADER_BG   = new Color(173, 216, 230);
    private static final Color CLR_SIDEBAR_BG  = new Color(200, 225, 240);
    private static final Color CLR_FIELD_BG    = new Color(173, 216, 230);
    private static final Color CLR_DISCHARGED  = new Color(160, 160, 160);
    private static final Color CLR_BTN         = new Color(210, 210, 210);
    private static final Color CLR_FIELD_OK    = new Color(140, 180, 210);
    private static final Color CLR_FIELD_ERROR = new Color(200,  60,  60);
    private static final Font FONT_TITLE      = new Font("SansSerif", Font.BOLD,  18);
    private static final Font FONT_LABEL      = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_PATIENT_ID = new Font("SansSerif", Font.BOLD,  13);
    private static final Font FONT_PATIENT_NM = new Font("SansSerif", Font.PLAIN, 11);

    // Validation regexes
    /**
     * Full name: first + last, letters/spaces/hyphens/apostrophes/commas/periods.
     * At least two whitespace-separated tokens are enforced separately.
     */
    private static final String RX_NAME     = "[A-Za-z][A-Za-z ,.'-]{1,99}";
    /**
     * Age: integer 1–150
     * The range check happens in validateFields().
     */
    private static final String RX_AGE      = "\\d{1,3}";
    /**
     * Height: accepts common formats —
     *   imperial:  5'11"  |  5' 11"  |  5'11  |  6'
     *   metric:    180cm  |  180 cm  |  1.80m  |  180
     *   decimal:   5.11
     */
    private static final String RX_HEIGHT   =
        "\\d{1,3}(['\"]\\s*\\d{0,2}['\"]?|\\s*(cm|m)|(\\.\\d{1,2})?)?";
    /**
     * Weight: positive decimal (e.g. 70, 70.5, 0.5).
     * The range check happens in validateFields().
     */
    private static final String RX_WEIGHT   = "\\d{1,4}(\\.\\d{1,2})?";

    // Severity: single digit 1–4. 
    private static final String RX_SEVERITY = "[1-4]";

    // State
    private final ServerConnection conn;
    private final boolean isDoctor;
    private Patient currentPatient = null;

    // Sidebar widgets
    private JTextField searchField;
    private JPanel patientListPanel;

    // Content-area widgets
    private JLabel patientIdLabel;

    private JTextField nameField;
    private JTextField ageField;
    private JTextField heightField;
    private JTextField weightField;
    private JTextArea conditionArea;
    private JTextField severityField;
    private JTextArea notesArea; // doctor only

    private JButton billingButton;
    private JButton dischargeButton; // doctor only
    private JButton actionButton; // "Create Patient" | "Update Patient"

    // Constructor
    public MainWindow(ServerConnection conn, boolean isDoctor) {
        this.conn = conn;
        this.isDoctor = isDoctor;

        setTitle("Hospital Management System — " + (isDoctor ? "Doctor" : "Nurse"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 660);
        setMinimumSize(new Dimension(760, 560));
        setLocationRelativeTo(null);

        buildUI();
        refreshPatientList();

        if (!isDoctor) {
            showNewPatientForm(); // nurses start on the blank New Patient tab
        }

        // Immediately notified when the IO thread detects server disconnect
        conn.setOnDisconnect(this::handleServerDisconnect);

        startHeartbeat();
    }

    // UI Construction
    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);
        add(buildContentArea(), BorderLayout.CENTER);
    }

    // Header
    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_HEADER_BG);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.GRAY));
        panel.setPreferredSize(new Dimension(0, 56));

        JLabel logo = new JLabel("🏥  Hospital Management System", SwingConstants.CENTER);
        logo.setFont(FONT_TITLE);
        logo.setForeground(new Color(30, 70, 110));
        panel.add(logo, BorderLayout.CENTER);

        JLabel roleLabel = new JLabel((isDoctor ? "Doctor" : "Nurse") + "  ", SwingConstants.RIGHT);
        roleLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        roleLabel.setForeground(new Color(60, 90, 130));
        panel.add(roleLabel, BorderLayout.EAST);

        return panel;
    }

    // Left sidebar
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 4));
        sidebar.setBackground(CLR_SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(205, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 2, Color.GRAY));

        // "Patients" heading
        JLabel patientsTitle = new JLabel("Patients", SwingConstants.CENTER);
        patientsTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        patientsTitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        patientsTitle.setBackground(CLR_SIDEBAR_BG);
        patientsTitle.setOpaque(true);

        // Search bar
        searchField = new JTextField();
        searchField.setToolTipText("Search by patient ID or name");
        searchField.setFont(FONT_LABEL);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                filterPatientList();
            }

            public void removeUpdate(DocumentEvent e) {
                filterPatientList();
            }

            public void changedUpdate(DocumentEvent e) {
                filterPatientList();
            }
        });

        JPanel topBar = new JPanel(new BorderLayout(0, 3));
        topBar.setBackground(CLR_SIDEBAR_BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        topBar.add(patientsTitle, BorderLayout.NORTH);
        topBar.add(searchField, BorderLayout.CENTER);

        // Scrollable list of patient buttons
        patientListPanel = new JPanel();
        patientListPanel.setLayout(new BoxLayout(patientListPanel, BoxLayout.Y_AXIS));
        patientListPanel.setBackground(CLR_SIDEBAR_BG);

        JScrollPane scroll = new JScrollPane(patientListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        sidebar.add(topBar, BorderLayout.NORTH);
        sidebar.add(scroll, BorderLayout.CENTER);
        return sidebar;
    }

    // Right content area
    private JPanel buildContentArea() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.WHITE);

        // Patient ID label
        patientIdLabel = new JLabel(" ", SwingConstants.CENTER);
        patientIdLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        patientIdLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));
        patientIdLabel.setBackground(new Color(240, 247, 252));
        patientIdLabel.setOpaque(true);

        JPanel form = buildFormPanel();
        JScrollPane formScroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        content.add(patientIdLabel, BorderLayout.NORTH);
        content.add(formScroll, BorderLayout.CENTER);
        content.add(buildButtonBar(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.EAST;
        lc.insets = new Insets(6, 4, 6, 10);

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(6, 0, 6, 4);

        int row = 0;

        // the fields
        nameField = makeTextField();
        attachValidator(nameField, RX_NAME);
        addFormRow(form, "Name:", nameField, lc, fc, row++);

        ageField = makeTextField();
        attachValidator(ageField, RX_AGE);
        addFormRow(form, "Age:", ageField, lc, fc, row++);

        heightField = makeTextField();
        attachValidator(heightField, RX_HEIGHT);
        addFormRow(form, "Height:", heightField, lc, fc, row++);

        weightField = makeTextField();
        attachValidator(weightField, RX_WEIGHT);
        addFormRow(form, "Weight:", weightField, lc, fc, row++);

        conditionArea = makeTextArea(4);
        JScrollPane conditionScroll = scrolledArea(conditionArea);
        attachAreaValidator(conditionArea, conditionScroll);
        addFormRow(form, "Condition:", conditionScroll, lc, fc, row++);

        // Severity — narrow field, left-aligned
        severityField = makeTextField();
        attachValidator(severityField, RX_SEVERITY);
        severityField.setPreferredSize(new Dimension(55, 26));
        GridBagConstraints sevfc = (GridBagConstraints) fc.clone();
        sevfc.fill = GridBagConstraints.NONE;
        sevfc.weightx = 0;
        sevfc.anchor = GridBagConstraints.WEST;
        addFormRow(form, "Severity:", severityField, lc, sevfc, row++);

        // Notes — doctor only
        if (isDoctor) {
            notesArea = makeTextArea(4);
            JScrollPane notesScroll = scrolledArea(notesArea);
            attachAreaValidator(notesArea, notesScroll);
            addFormRow(form, "Notes:", notesScroll, lc, fc, row++);
        }

        // Vertical filler
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), filler);

        return form;
    }

    private JPanel buildButtonBar() {
        // BorderLayout: left buttons on WEST, action button on EAST
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(225, 237, 248));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        // Left side: Billing Info (and Discharge for doctors)
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        billingButton = makeButton("Billing Info");
        billingButton.addActionListener(e -> openBillingDialog());
        left.add(billingButton);

        if (isDoctor) {
            dischargeButton = makeButton("Discharge Patient");
            dischargeButton.addActionListener(e -> dischargeCurrentPatient());
            left.add(dischargeButton);
        }

        // Right side: Create Patient (nurse) or Update Patient (doctor)
        String actionLabel = isDoctor ? "Update Patient" : "Create Patient";
        actionButton = makeButton(actionLabel);
        actionButton.addActionListener(e -> performAction());

        bar.add(left, BorderLayout.WEST);
        bar.add(actionButton, BorderLayout.EAST);

        return bar;
    }

    // Patient list
    public void refreshPatientList() {
        SwingWorker<List<Patient>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Patient> doInBackground() throws Exception {
                return conn.getAllPatients();
            }

            @Override
            protected void done() {
                try {
                    rebuildPatientList(get());
                } catch (Exception ex) {
                    showError("Failed to load patient list: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void rebuildPatientList(List<Patient> patients) {
        patientListPanel.removeAll();

        if (!isDoctor) {
            patientListPanel.add(makeNewPatientButton());
        }

        for (Patient p : patients) {
            patientListPanel.add(makePatientButton(p));
        }

        patientListPanel.revalidate();
        patientListPanel.repaint();
    }

    private JButton makeNewPatientButton() {
        JButton btn = new JButton("New Patient");
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setBackground(new Color(185, 215, 240));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        btn.addActionListener(e -> showNewPatientForm());
        return btn;
    }

    private JButton makePatientButton(Patient p) {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(2, 0));

        JLabel idLbl = new JLabel("ID# " + p.getId());
        idLbl.setFont(FONT_PATIENT_ID);

        JLabel nameLbl = new JLabel(p.getFullName());
        nameLbl.setFont(FONT_PATIENT_NM);
        nameLbl.setForeground(new Color(60, 60, 60));

        btn.add(idLbl, BorderLayout.NORTH);
        btn.add(nameLbl, BorderLayout.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        if (p.isDischarged()) {
            btn.setBackground(CLR_DISCHARGED);
            idLbl.setForeground(Color.DARK_GRAY);
        } else {
            btn.setBackground(CLR_SIDEBAR_BG);
        }

        btn.addActionListener(e -> showPatient(p));
        return btn;
    }

    private void filterPatientList() {
        String query = searchField.getText().trim().toLowerCase();
        for (Component c : patientListPanel.getComponents()) {
            if (c instanceof JButton btn) {
                String combined = collectText(btn).toLowerCase();
                btn.setVisible(query.isEmpty() || combined.contains(query));
            }
        }
        patientListPanel.revalidate();
        patientListPanel.repaint();
    }


    private String collectText(Container container) {
        StringBuilder sb = new StringBuilder();
        for (Component c : container.getComponents()) {
            if (c instanceof JLabel lbl)
                sb.append(lbl.getText()).append(' ');
            else if (c instanceof Container sub)
                sb.append(collectText(sub));
        }
        return sb.toString();
    }

    // Form population

    private void showNewPatientForm() {
        currentPatient = null;
        patientIdLabel.setText(" ");

        nameField.setText("");
        ageField.setText("");
        heightField.setText("");
        weightField.setText("");
        conditionArea.setText("");
        severityField.setText("");
        if (notesArea != null)
            notesArea.setText("");

        setFieldsEditable(true);
        billingButton.setEnabled(false);
        actionButton.setText("Create Patient");
        actionButton.setEnabled(true);
    }

    private void showPatient(Patient p) {
        currentPatient = p;
        patientIdLabel.setText("ID# " + p.getId());

        nameField.setText(p.getFullName());
        ageField.setText(String.valueOf(p.getAge()));
        heightField.setText(p.getHeight());
        weightField.setText(String.valueOf(p.getWeight()));
        conditionArea.setText(p.getCondition());
        severityField.setText(String.valueOf(p.getSeverity()));
        if (notesArea != null)
            notesArea.setText(p.getDoctorNotes());

        boolean discharged = p.isDischarged();
        setFieldsEditable(!discharged);

        billingButton.setEnabled(true);
        actionButton.setText("Update Patient");
        actionButton.setEnabled(!discharged);

        if (isDoctor && dischargeButton != null) {
            dischargeButton.setEnabled(!discharged);
        }
    }

    // Make the fields editable
    private void setFieldsEditable(boolean editable) {
        nameField.setEditable(editable);
        ageField.setEditable(editable);
        heightField.setEditable(editable);
        weightField.setEditable(editable);
        conditionArea.setEditable(editable);
        severityField.setEditable(editable);
        if (notesArea != null)
            notesArea.setEditable(editable);

        Color bg = editable ? CLR_FIELD_BG : new Color(210, 210, 210);
        nameField.setBackground(bg);
        ageField.setBackground(bg);
        heightField.setBackground(bg);
        weightField.setBackground(bg);
        conditionArea.setBackground(bg);
        severityField.setBackground(bg);
        if (notesArea != null)
            notesArea.setBackground(bg);
    }

    // Actions

    private void performAction() {
        // validate all fields using regex rules
        String error = validateFields();
        if (error != null) {
            showError(error);
            return;
        }

        // safe to parse after validation
        String name      = nameField.getText().trim();
        String[] nameParts = name.split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName  = nameParts[1];
        int    age       = Integer.parseInt(ageField.getText().trim());
        String height    = heightField.getText().trim();
        double weight    = Double.parseDouble(weightField.getText().trim());
        String cond      = conditionArea.getText().trim();
        int    severity  = Integer.parseInt(severityField.getText().trim());
        String notes     = (notesArea != null) ? notesArea.getText().trim() : "";

        if (currentPatient == null) {
            // the nurse creates a new patient
            Patient p = new Patient(0, firstName, lastName, age,
                    height, weight, "", cond, severity, notes, false);
            SwingWorker<Integer, Void> worker = new SwingWorker<>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    return conn.createPatient(p);
                }

                @Override
                protected void done() {
                    try {
                        int newId = get();
                        if (newId < 0) {
                            showError("Server rejected patient creation.");
                        } else {
                            p.setDischarged(false);
                            refreshPatientList();
                            // reload so we can show the assigned ID
                            Patient created = conn.getPatient(newId);
                            if (created != null)
                                showPatient(created);
                        }
                    } catch (Exception ex) {
                        showError("Failed to create patient: " + ex.getMessage());
                    }
                }
            };
            worker.execute();
        } else {
            // Update existing patient
            currentPatient.setFirstName(firstName);
            currentPatient.setLastName(lastName);
            currentPatient.setAge(age);
            currentPatient.setHeight(height);
            currentPatient.setWeight(weight);
            currentPatient.setCondition(cond);
            currentPatient.setSeverity(severity);
            currentPatient.setDoctorNotes(notes);

            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    return conn.updatePatient(currentPatient);
                }

                @Override
                protected void done() {
                    try {
                        boolean ok = get();
                        if (ok) {
                            refreshPatientList();
                            JOptionPane.showMessageDialog(MainWindow.this,
                                    "Patient updated successfully.", "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            showError("Server rejected the update.");
                        }
                    } catch (Exception ex) {
                        showError("Failed to update patient: " + ex.getMessage());
                    }
                }
            };
            worker.execute();
        }
    }

    private void dischargeCurrentPatient() {
        if (currentPatient == null)
            return;
        if (currentPatient.getSeverity() != 4) {
            showError("Discharge is only allowed when Severity = 4.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Discharge " + currentPatient.getFullName() + "?\n"
                        + "Their record will become read-only.",
                "Confirm Discharge", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION)
            return;

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return conn.dischargePatient(currentPatient.getId());
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        currentPatient.setDischarged(true);
                        refreshPatientList();
                        showPatient(currentPatient);
                    } else {
                        showError("Server rejected discharge.");
                    }
                } catch (Exception ex) {
                    showError("Failed to discharge patient: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void openBillingDialog() {
        if (currentPatient == null)
            return;
        if (isDoctor) {
            new DoctorBillingDialog(this, conn, currentPatient).setVisible(true);
        } else {
            new NurseBillingDialog(this, conn, currentPatient).setVisible(true);
        }
    }

    // UI helpers

    private JTextField makeTextField() {
        JTextField tf = new JTextField();
        tf.setBackground(CLR_FIELD_BG);
        tf.setFont(FONT_LABEL);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CLR_FIELD_OK),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        return tf;
    }

    private JTextArea makeTextArea(int rows) {
        JTextArea ta = new JTextArea(rows, 0);
        ta.setBackground(CLR_FIELD_BG);
        ta.setFont(FONT_LABEL);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }

    private JScrollPane scrolledArea(JTextArea ta) {
        JScrollPane sp = new JScrollPane(ta,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createLineBorder(CLR_FIELD_OK));
        return sp;
    }

    // Real-time validation helpers
    private void attachValidator(JTextField field, String regex) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { recheck(); }
            public void removeUpdate(DocumentEvent e)  { recheck(); }
            public void changedUpdate(DocumentEvent e) { recheck(); }

            private void recheck() {
                String text = field.getText().trim();
                boolean ok = text.isEmpty() || text.matches(regex);
                Color border = ok ? CLR_FIELD_OK : CLR_FIELD_ERROR;
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border),
                        BorderFactory.createEmptyBorder(3, 5, 3, 5)));
            }
        });
    }

    
     // Rule: text must be 500 characters or fewer (blank is allowed while typing).
     
    private void attachAreaValidator(JTextArea area, JScrollPane scroll) {
        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { recheck(); }
            public void removeUpdate(DocumentEvent e)  { recheck(); }
            public void changedUpdate(DocumentEvent e) { recheck(); }

            private void recheck() {
                String text = area.getText().trim();
                boolean ok = text.isEmpty() || text.length() <= 500;
                scroll.setBorder(BorderFactory.createLineBorder(
                        ok ? CLR_FIELD_OK : CLR_FIELD_ERROR));
            }
        });
    }

    /**
     * Validates all editable patient fields.
     *
     * @return an error message string, or {@code null} if everything is valid
     */
    private String validateFields() {
        String name = nameField.getText().trim();
        if (!name.matches(RX_NAME))
            return "Name may only contain letters, spaces, hyphens, apostrophes, commas, and periods.";
        if (name.split("\\s+").length < 2)
            return "Name must include at least a first and last name.";

        String ageStr = ageField.getText().trim();
        if (!ageStr.matches(RX_AGE))
            return "Age must be a whole number (e.g. 34).";
        int age = Integer.parseInt(ageStr);
        if (age < 1 || age > 150)
            return "Age must be between 1 and 150.";

        String height = heightField.getText().trim();
        if (height.isEmpty())
            return "Height is required (e.g. 5'11\" or 180 cm).";
        if (!height.matches(RX_HEIGHT))
            return "Height format not recognised. Use e.g. 5'11\", 180 cm, or 180.";
        if (height.length() > 20)
            return "Height value is too long (max 20 characters).";

        String wtStr = weightField.getText().trim();
        if (!wtStr.matches(RX_WEIGHT))
            return "Weight must be a positive number (e.g. 70 or 70.5).";
        double weight = Double.parseDouble(wtStr);
        if (weight <= 0)
            return "Weight must be greater than 0.";

        String cond = conditionArea.getText().trim();
        if (cond.isEmpty())
            return "Condition cannot be blank.";
        if (cond.length() > 500)
            return "Condition must be 500 characters or fewer.";

        String sevStr = severityField.getText().trim();
        if (!sevStr.matches(RX_SEVERITY))
            return "Severity must be a single digit: 1, 2, 3, or 4.";

        if (notesArea != null) {
            String notes = notesArea.getText().trim();
            if (notes.length() > 500)
                return "Notes must be 500 characters or fewer.";
        }

        return null;
    }

    private JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(CLR_BTN);
        b.setFont(FONT_LABEL);
        b.setFocusPainted(false);
        return b;
    }

    private void addFormRow(JPanel form, String labelText, Component field,
            GridBagConstraints lc, GridBagConstraints fc, int row) {
        lc.gridy = row;
        lc.gridx = 0;
        fc.gridy = row;
        fc.gridx = 1;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_LABEL);
        form.add(lbl, lc);
        form.add(field, fc);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Server disconnect detection

    // Guards against showing the disconnected dialog more than once.
    private volatile boolean disconnectHandled = false;

    /**
     * Polls the server every 5 seconds with a lightweight ping.
     * Acts as a fallback — the IO thread's onDisconnect callback fires
     * immediately when the connection drops. The heartbeat catches cases
     * where the socket stays open but the server is no longer responding.
     */
    private void startHeartbeat() {
        Thread heartbeat = new Thread(() -> {
            while (!disconnectHandled) {
                try {
                    Thread.sleep(5000);
                    if (disconnectHandled) break;
                    boolean alive = conn.ping();
                    if (!alive) {
                        handleServerDisconnect();
                        return;
                    }
                } catch (InterruptedException e) {
                    return; // window closed cleanly
                } catch (Exception e) {
                    handleServerDisconnect();
                    return;
                }
            }
        });
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private void handleServerDisconnect() {
        // Ensure we only show the dialog once, even if both the IO thread
        // callback and the heartbeat fire at nearly the same time.
        if (disconnectHandled) return;
        disconnectHandled = true;

        SwingUtilities.invokeLater(() -> {
            setEnabled(false);
            JOptionPane.showMessageDialog(
                    null,
                    "The server has shut down or the connection was lost.\nYou have been disconnected.",
                    "Disconnected",
                    JOptionPane.WARNING_MESSAGE);
            conn.disconnect();
            dispose();
            new LoginFrame().setVisible(true);
        });
    }
}