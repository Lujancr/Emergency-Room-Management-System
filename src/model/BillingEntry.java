package src.model;

//New
import java.io.Serializable;

public class BillingEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private String procedureName;
    private double cost;

    public BillingEntry(String procedureName, double cost) {
        this.procedureName = procedureName;
        this.cost = cost;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public double getCost() {
        return cost;
    }

    /**
     * Serialize to pipe-delimited line for billing_<patientId>.txt
     * Format: procedureName|cost
     */
    public String toFileLine() {
        return procedureName.replace("|", ";;") + "|" + cost;
    }

    public static BillingEntry fromFileLine(String line) {
        String[] parts = line.split("\\|", 2);
        if (parts.length < 2)
            return null;
        String name = parts[0].replace(";;", "|");
        double cost = Double.parseDouble(parts[1].trim());
        return new BillingEntry(name, cost);
    }

    @Override
    public String toString() {
        return procedureName + " - $" + String.format("%.2f", cost);
    }
}