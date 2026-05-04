package src.model;

/*

*/
import java.io.Serializable;

public class Patient implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String firstName;
    private String lastName;
    private int age;
    private String height;
    private double weight;
    private String ssn;
    private String condition;
    private int severity; // 1-3 (nurse), 4 = discharged (doctor-only action)
    private String doctorNotes;
    private boolean discharged;

    public Patient(int id, String firstName, String lastName, int age,
            String height, double weight, String ssn,
            String condition, int severity, String doctorNotes, boolean discharged) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.height = height;
        this.weight = weight;
        this.ssn = ssn;
        this.condition = condition;
        this.severity = severity;
        this.doctorNotes = doctorNotes;
        this.discharged = discharged;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public int getAge() {
        return age;
    }

    public String getHeight() {
        return height;
    }

    public double getWeight() {
        return weight;
    }

    public String getSsn() {
        return ssn;
    }

    public String getCondition() {
        return condition;
    }

    public int getSeverity() {
        return severity;
    }

    public String getDoctorNotes() {
        return doctorNotes;
    }

    public boolean isDischarged() {
        return discharged;
    }

    // Setters
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public void setDoctorNotes(String notes) {
        this.doctorNotes = notes;
    }

    public void setDischarged(boolean discharged) {
        this.discharged = discharged;
    }

    // Method to convert a Patient object to a line for patients.txt
    public String toFileLine() {
        return id + "|" + firstName + "|" + lastName + "|" + age + "|" + height + "|"
                + weight + "|" + ssn + "|" + condition.replace("|", ";;") + "|"
                + severity + "|" + doctorNotes.replace("|", ";;") + "|" + discharged;
    }

    // Factory method 1: rebuilds a Patient from a saved line in patients.txt
    public static Patient fromFileLine(String line) {
        String[] parts = line.split("\\|", 11);
        if (parts.length < 11)
            return null;
        int id = Integer.parseInt(parts[0].trim());
        String firstName = parts[1].trim();
        String lastName = parts[2].trim();
        int age = Integer.parseInt(parts[3].trim());
        String height = parts[4].trim();
        double weight = Double.parseDouble(parts[5].trim());
        String ssn = parts[6].trim();
        String condition = parts[7].replace(";;", "|");
        int severity = Integer.parseInt(parts[8].trim());
        String notes = parts[9].replace(";;", "|");
        boolean discharged = Boolean.parseBoolean(parts[10].trim());
        return new Patient(id, firstName, lastName, age, height, weight, ssn, condition, severity, notes, discharged);
    }

    // Factory method 2: creates a blank new patient with safe defaults.
    // Used when a nurse opens the New Patient tab — ID is 0 until FileManager
    // assigns one.
    public static Patient createNew(String firstName, String lastName) {
        return new Patient(0, firstName, lastName, 0, "", 0.0, "", "", 1, "", false);
    }

    @Override
    public String toString() {
        return "Patient{id=" + id + ", name=" + getFullName() + ", severity=" + severity + "}";
    }
}
