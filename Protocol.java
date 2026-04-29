public class Protocol {
    public static final String LOGIN = "LOGIN";
    public static final String GET_ALL_PATIENTS = "GET_ALL_PATIENTS";
    public static final String GET_PATIENT = "GET_PATIENT";
    public static final String CREATE_PATIENT = "CREATE_PATIENT";
    public static final String UPDATE_PATIENT = "UPDATE_PATIENT";
    public static final String DISCHARGE_PATIENT = "DISCHARGE_PATIENT";
    public static final String GET_BILL = "GET_BILL";
    public static final String ADD_BILL_ENTRY = "ADD_BILL_ENTRY";
    public static final String DELETE_BILL_ENTRY = "DELETE_BILL_ENTRY";

    //Misc
    public static final String OK    = "OK";
    public static final String ERROR = "ERROR";
    public static final String SEP   = "\t";   // field separator in protocol messages
    public static final String ROW_SEP = "\n"; // row separator inside data payloads

    // Role strings returned on successful login
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String ROLE_NURSE  = "NURSE";
}
