import java.io.Serializable;

//This object can be converted into a byte stream and saved or transferred.
public class StaffUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String password;
    private boolean isDoctor; // true = Doctor, false = Nurse

    public StaffUser(String userId, String password, boolean isDoctor) {
        this.userId   = userId;
        this.password = password;
        this.isDoctor = isDoctor;
    }

    public String getUserId()   { return userId; }
    public String getPassword() { return password; }
    public boolean isDoctor()   { return isDoctor; }

    /**
     * Serialize to pipe-delimited line for staff.txt
     * Format: userId|password|isDoctor
     */
    public String toFileLine() {
        return userId + "|" + password + "|" + isDoctor;
    }

    public static StaffUser fromFileLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 3) return null;
        return new StaffUser(parts[0].trim(), parts[1].trim(), Boolean.parseBoolean(parts[2].trim()));
    }
}
