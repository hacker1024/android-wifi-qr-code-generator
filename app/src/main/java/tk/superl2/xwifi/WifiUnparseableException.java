package tk.superl2.xwifi;

public class WifiUnparseableException extends Exception {
    public WifiUnparseableException(String message) {
        super(message);
    }

    public WifiUnparseableException() {
        super("Wifi list could not be parsed.");
    }
}
