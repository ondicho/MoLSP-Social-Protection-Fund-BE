package MoLSP.go.ke.Social_Assistance_Fund.exceptions;

public class InvalidPhoneNumberException extends IllegalArgumentException {
    public InvalidPhoneNumberException() {
        super("A valid phone number from an allowed country is required");
    }
}
