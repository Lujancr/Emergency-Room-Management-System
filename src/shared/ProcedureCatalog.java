package src.shared;

import java.util.*;

// Catalog of medical procedures and their costs. This is a static utility class
// that provides read-only access to the procedure data. The catalog is immutable
// and thread-safe, as it is initialized once and never modified thereafter.
public class ProcedureCatalog {

    // Static map of procedure names to their costs. LinkedHashMap is used to
    // preserve insertion order for display purposes. The map is wrapped in
    // Collections.unmodifiableMap to prevent modification after initialization.
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

    // Returns an unmodifiable view of the procedure catalog. The keys are procedure
    // names and the values are their costs. This allows clients to read the catalog
    // without risking modification. The catalog is immutable and thread-safe, so it
    // can be safely shared across threads without synchronization
    public static Map<String, Double> getCatalog() {
        return CATALOG;
    }

    // Returns a list of all procedure names in the catalog. This is a convenience
    // method for clients that only need the names. The list is a new ArrayList to
    // prevent modification of the underlying catalog keys.
    public static List<String> getProcedureNames() {
        return new ArrayList<>(CATALOG.keySet());
    }

    // Returns the cost of a procedure by name. If the procedure does not exist,
    // returns 0.0. This method provides a simple way to look up costs without
    // exposing the entire catalog.
    public static double getCost(String procedureName) {
        return CATALOG.getOrDefault(procedureName, 0.0);
    }

    // Returns a formatted string for display purposes, showing the procedure name
    // and its cost. This is a convenience method for clients that want to display
    // the procedure information in a user-friendly format.
    public static String displayString(String procedureName) {
        double cost = getCost(procedureName);
        return String.format("%s — $%,.2f", procedureName, cost);
    }
}
