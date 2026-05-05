package src.model;
import java.io.Serializable;
/*
*Stores procedures name and cost
*implemets sericalizable for objects persistence and network transfer
*Handles delimiter escaping to preserve data
*Overrides toString() for user friendly display
*/
public class BillingEntry implements Serializable {
//Ensure compatibility during serialization
    private static final long serialVersionUID = 1L;
//Name of the medical procedure or service
    private String procedureName;
//Cost of procedure of service
    private double cost;
//Constructs a billing entry with a procedure name and cost
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

/* Serialize to pipe-delimited line for billing_<patientId>.txt
* Format: procedureName|cost
*/
    public String toFileLine() {
        return procedureName.replace("|", ";;") + "|" + cost;
    }
//Reconstructs a billing entry object from a pip-delimited string
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