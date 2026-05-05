package src.gui;

import src.client.ServerConnection;
import src.model.BillingEntry;
import src.model.Patient;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

public class NurseBillingDialog extends JDialog {

    // Custom colors and fonts for styling the billing popup
    private static final Color CLR_BG = new Color(173, 216, 230);
    private static final Color CLR_LIST_BG = Color.WHITE;
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 14);
    private static final Font FONT_ITEM = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_TOTAL = new Font("SansSerif", Font.BOLD, 13);

    // References to server connection and patient data for loading billing info
    private final ServerConnection conn;
    private final Patient patient;

    // UI components for displaying the list of billing entries and the total cost
    private DefaultListModel<String> listModel;
    private JLabel totalLabel;

    // Constructor method that sets up the billing dialog with patient info and server connection
    public NurseBillingDialog(JFrame owner, ServerConnection conn, Patient patient) {
        super(owner, "Billing Info — Patient " + patient.getId(), true);
        this.conn = conn;
        this.patient = patient;

        buildUI();
        setSize(400, 480);
        setResizable(false);
        setLocationRelativeTo(owner);

        loadBillingData();
    }

    // Method that creates the UI components and arranges them in the dialog
    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 14));
        main.setBackground(CLR_BG);
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("ID# " + patient.getId() + "  Billing Info", SwingConstants.CENTER);
        title.setFont(FONT_TITLE);
        main.add(title, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        JList<String> treatmentList = new JList<>(listModel);
        treatmentList.setFont(FONT_ITEM);
        treatmentList.setBackground(CLR_LIST_BG);
        treatmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        treatmentList.setEnabled(false); 
        treatmentList.setFixedCellHeight(32);

        treatmentList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel(value, SwingConstants.CENTER);
            lbl.setFont(FONT_ITEM);
            lbl.setOpaque(true);
            lbl.setBackground(CLR_LIST_BG);
            lbl.setForeground(Color.BLACK);
            lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
            return lbl;
        });

        JScrollPane scroll = new JScrollPane(treatmentList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        main.add(scroll, BorderLayout.CENTER);

        totalLabel = new JLabel("Total Cost:  Loading…");
        totalLabel.setFont(FONT_TOTAL);
        totalLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 0));
        main.add(totalLabel, BorderLayout.SOUTH);

        setContentPane(main);
    }

    // Method to load billing data from the server in a background thread and update the UI when done
    private void loadBillingData() {
        SwingWorker<List<BillingEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<BillingEntry> doInBackground() throws Exception {
                return conn.getBill(patient.getId());
            }

            @Override
            protected void done() {
                try {
                    List<BillingEntry> items = get();
                    listModel.clear();
                    double total = 0.0;
                    for (BillingEntry item : items) {
                        listModel.addElement(item.getProcedureName());
                        total += item.getCost();
                    }
                    totalLabel.setText(String.format("Total Cost:  $%.2f", total));
                } catch (Exception ex) {
                    totalLabel.setText("Total Cost:  (error loading)");
                    JOptionPane.showMessageDialog(NurseBillingDialog.this,
                            "Failed to load billing data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}