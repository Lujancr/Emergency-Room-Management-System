package src.gui;

import src.client.ServerConnection;
import src.model.BillingEntry;
import src.model.Patient;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

/**
 * NurseBillingDialog — read-only billing info popup for nurses.
 *
 * Shows all treatment methods / operations that the doctor has released
 * for the selected patient, plus a total cost at the bottom.
 *
 * Wireframe reference: "Billing window for Nurses"
 * ┌─────────────────────────────┐
 * │ ID# Billing Info │
 * │ ┌───────────────────────┐ │
 * │ │ Treatment Method #1 │ │
 * │ │ Treatment Method #2 │ │ ← scrollable list (read-only)
 * │ │ ... │ │
 * │ └───────────────────────┘ │
 * │ Total Cost: $XXX.XX │
 * └─────────────────────────────┘
 */
public class NurseBillingDialog extends JDialog {

    private static final Color CLR_BG = new Color(173, 216, 230);
    private static final Color CLR_LIST_BG = Color.WHITE;
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 14);
    private static final Font FONT_ITEM = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_TOTAL = new Font("SansSerif", Font.BOLD, 13);

    private final ServerConnection conn;
    private final Patient patient;

    private DefaultListModel<String> listModel;
    private JLabel totalLabel;

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

    // ─────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 14));
        main.setBackground(CLR_BG);
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Title
        JLabel title = new JLabel("ID# " + patient.getId() + "  Billing Info", SwingConstants.CENTER);
        title.setFont(FONT_TITLE);
        main.add(title, BorderLayout.NORTH);

        // Scrollable treatment list (read-only)
        listModel = new DefaultListModel<>();
        JList<String> treatmentList = new JList<>(listModel);
        treatmentList.setFont(FONT_ITEM);
        treatmentList.setBackground(CLR_LIST_BG);
        treatmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        treatmentList.setEnabled(false); // read-only for nurse
        treatmentList.setFixedCellHeight(32);

        // Render items centered with borders between them
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

        // Bottom: total cost
        totalLabel = new JLabel("Total Cost:  Loading…");
        totalLabel.setFont(FONT_TOTAL);
        totalLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 0));
        main.add(totalLabel, BorderLayout.SOUTH);

        setContentPane(main);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────

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