package src.gui;

import src.client.ServerConnection;
import src.model.BillingEntry;
import src.model.Patient;
import src.shared.ProcedureCatalog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DoctorBillingDialog — editable billing popup for doctors.
 *
 * Top panel  : scrollable catalogue of all available procedures.
 *              Click a procedure to add it to the bill.
 *              Hovering shows the price as a tooltip.
 * Bottom panel: the patient's current bill.
 *              Select an item and press "Delete Cost" to remove it.
 * "Update Bill" sends the full bill to the server.
 *
 *   ┌──────────────────────────────────────────┐
 *   │       ID# XXXX  Billing Info             │
 *   │  ┌────────────────────────────────────┐  │
 *   │  │ General Consultation — $150.00     │  │  ← catalogue (click to add)
 *   │  │ Blood Test — $200.00               │  │
 *   │  │         ...                        │  │
 *   │  └────────────────────────────────────┘  │
 *   │             Bill                         │
 *   │  ┌────────────────────────────────────┐  │
 *   │  │ General Consultation               │  │  ← selected items
 *   │  └────────────────────────────────────┘  │
 *   │  [ Update Bill ]   [ Delete Cost ]       │
 *   └──────────────────────────────────────────┘
 */
public class DoctorBillingDialog extends JDialog {

    private static final Color CLR_BG      = new Color(173, 216, 230);
    private static final Color CLR_LIST_BG = Color.WHITE;
    private static final Color CLR_SEL_BG  = new Color(173, 216, 230);
    private static final Font  FONT_TITLE  = new Font("SansSerif", Font.BOLD, 14);
    private static final Font  FONT_SUB    = new Font("SansSerif", Font.BOLD, 12);
    private static final Font  FONT_ITEM   = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font  FONT_BTN    = new Font("SansSerif", Font.PLAIN, 13);

    private final ServerConnection conn;
    private final Patient          patient;

    /** All available procedures (name → cost). */
    private final Map<String, Double> catalog = ProcedureCatalog.getCatalog();
    private final List<String>        catalogNames = ProcedureCatalog.getProcedureNames();

    /** Items on the patient's bill currently being edited. */
    private final List<BillingEntry> billItems = new ArrayList<>();

    private DefaultListModel<String> catalogModel;
    private DefaultListModel<String> billModel;
    private JList<String>            catalogList;
    private JList<String>            billList;

    public DoctorBillingDialog(JFrame owner, ServerConnection conn, Patient patient) {
        super(owner, "Billing Info — Patient " + patient.getId(), true);
        this.conn    = conn;
        this.patient = patient;

        buildUI();
        setSize(480, 600);
        setResizable(false);
        setLocationRelativeTo(owner);

        loadBill();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 12));
        main.setBackground(CLR_BG);
        main.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // Title
        JLabel title = new JLabel("ID# " + patient.getId() + "  Billing Info",
                SwingConstants.CENTER);
        title.setFont(FONT_TITLE);
        main.add(title, BorderLayout.NORTH);

        // Two list panels stacked
        JPanel centre = new JPanel(new GridLayout(2, 1, 0, 12));
        centre.setOpaque(false);
        centre.add(buildCataloguePanel());
        centre.add(buildBillPanel());
        main.add(centre, BorderLayout.CENTER);

        main.add(buildButtonBar(), BorderLayout.SOUTH);
        setContentPane(main);
    }

    private JPanel buildCataloguePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        catalogModel = new DefaultListModel<>();
        for (String name : catalogNames) {
            catalogModel.addElement(ProcedureCatalog.displayString(name));
        }

        catalogList = new JList<>(catalogModel);
        catalogList.setFont(FONT_ITEM);
        catalogList.setBackground(CLR_LIST_BG);
        catalogList.setSelectionBackground(CLR_SEL_BG);
        catalogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogList.setFixedCellHeight(34);
        catalogList.setCellRenderer(centeredRenderer());

        // Tooltip shows just the price when hovering
        catalogList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int idx = catalogList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < catalogNames.size()) {
                    String name = catalogNames.get(idx);
                    catalogList.setToolTipText(
                            String.format("$%,.2f", ProcedureCatalog.getCost(name)));
                } else {
                    catalogList.setToolTipText(null);
                }
            }
        });

        // Single-click adds to bill
        catalogList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = catalogList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < catalogNames.size()) {
                    String name = catalogNames.get(idx);
                    double cost = ProcedureCatalog.getCost(name);
                    addToBill(new BillingEntry(name, cost));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(catalogList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBillPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        JLabel billTitle = new JLabel("Bill", SwingConstants.CENTER);
        billTitle.setFont(FONT_SUB);

        billModel = new DefaultListModel<>();
        billList  = new JList<>(billModel);
        billList.setFont(FONT_ITEM);
        billList.setBackground(CLR_LIST_BG);
        billList.setSelectionBackground(CLR_SEL_BG);
        billList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        billList.setFixedCellHeight(34);
        billList.setCellRenderer(centeredRenderer());

        JScrollPane scroll = new JScrollPane(billList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        panel.add(billTitle, BorderLayout.NORTH);
        panel.add(scroll,    BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setOpaque(false);

        JButton updateBtn = new JButton("Update Bill");
        updateBtn.setFont(FONT_BTN);
        updateBtn.setBackground(new Color(210, 210, 210));
        updateBtn.setFocusPainted(false);
        updateBtn.addActionListener(e -> updateBill());

        JButton deleteBtn = new JButton("Delete Cost");
        deleteBtn.setFont(FONT_BTN);
        deleteBtn.setBackground(new Color(210, 210, 210));
        deleteBtn.setFocusPainted(false);
        deleteBtn.addActionListener(e -> deleteSelectedCost());

        bar.add(updateBtn);
        bar.add(deleteBtn);
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Data loading
    // ─────────────────────────────────────────────────────────────────────

    private void loadBill() {
        SwingWorker<List<BillingEntry>, Void> worker = new SwingWorker<>() {
            @Override protected List<BillingEntry> doInBackground() throws Exception {
                return conn.getBill(patient.getId());
            }
            @Override protected void done() {
                try {
                    List<BillingEntry> existing = get();
                    billItems.clear();
                    billModel.clear();
                    for (BillingEntry entry : existing) {
                        billItems.add(entry);
                        billModel.addElement(entry.getProcedureName());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DoctorBillingDialog.this,
                            "Failed to load billing data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Actions
    // ─────────────────────────────────────────────────────────────────────

    private void addToBill(BillingEntry entry) {
        billItems.add(entry);
        billModel.addElement(entry.getProcedureName());
    }

    private void deleteSelectedCost() {
        int idx = billList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a treatment from the Bill list first.",
                    "Nothing Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        BillingEntry removed = billItems.remove(idx);
        billModel.remove(idx);

        // Optimistically tell the server right away (removed from UI already)
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override protected Boolean doInBackground() throws Exception {
                return conn.deleteBillEntry(patient.getId(),
                        removed.getProcedureName(), removed.getCost());
            }
            @Override protected void done() {
                try {
                    if (!get()) {
                        showError("Server could not delete entry. Refresh and try again.");
                    }
                } catch (Exception ex) {
                    showError("Error communicating with server: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Sends every item currently in {@code billItems} to the server by
     * adding any that are new (optimistic: clears and re-adds everything).
     */
    private void updateBill() {
        // Take a snapshot to avoid concurrency issues
        List<BillingEntry> snapshot = new ArrayList<>(billItems);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                for (BillingEntry entry : snapshot) {
                    conn.addBillEntry(patient.getId(),
                            entry.getProcedureName(), entry.getCost());
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(DoctorBillingDialog.this,
                            "Bill updated successfully.", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    showError("Failed to update bill: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private ListCellRenderer<String> centeredRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel(value, SwingConstants.CENTER);
            lbl.setFont(FONT_ITEM);
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
            lbl.setBackground(isSelected ? CLR_SEL_BG : CLR_LIST_BG);
            lbl.setForeground(Color.BLACK);
            return lbl;
        };
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

