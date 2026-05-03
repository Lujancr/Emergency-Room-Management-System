package src.shared;

import java.util.*;

/**
 * Hardcoded catalogue of available procedures and their costs.
 * This is the single source of truth used by both the billing window
 * (to display options) and the server (for validation if desired).
 *
 * Add/remove entries here to update the available billing options.
 */
public class ProcedureCatalog {

    /** Immutable map: procedure name → cost */
    private static final Map<String, Double> CATALOG;

    static {
        Map<String, Double> m = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order for display
        m.put("General Consultation", 150.00);
        m.put("Blood Test", 200.00);
        m.put("X-Ray", 350.00);
        m.put("MRI Scan", 1200.00);
        m.put("CT Scan", 900.00);
        m.put("Ultrasound", 500.00);
        m.put("EKG / ECG", 250.00);
        m.put("Appendectomy", 8500.00);
        m.put("Heart Bypass Surgery", 45000.00);
        m.put("Knee Replacement", 15000.00);
        m.put("Physical Therapy Session", 120.00);
        m.put("Emergency Room Visit", 800.00);
        m.put("ICU Day Rate", 3000.00);
        m.put("General Anesthesia", 2000.00);
        m.put("IV Therapy", 300.00);
        m.put("Prescription Medication", 100.00);
        m.put("Colonoscopy", 1100.00);
        m.put("Endoscopy", 950.00);
        m.put("Dialysis Session", 500.00);
        m.put("Chemotherapy Session", 5000.00);
        CATALOG = Collections.unmodifiableMap(m);
    }

    /** Returns the full catalog as an unmodifiable map. */
    public static Map<String, Double> getCatalog() {
        return CATALOG;
    }

    /** Returns a list of procedure names in display order. */
    public static List<String> getProcedureNames() {
        return new ArrayList<>(CATALOG.keySet());
    }

    /** Returns the cost for a procedure, or 0.0 if not found. */
    public static double getCost(String procedureName) {
        return CATALOG.getOrDefault(procedureName, 0.0);
    }

    /** Returns a formatted display string: "Procedure Name — $1,200.00" */
    public static String displayString(String procedureName) {
        double cost = getCost(procedureName);
        return String.format("%s — $%,.2f", procedureName, cost);
    }
}
