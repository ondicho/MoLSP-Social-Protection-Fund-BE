package MoLSP.go.ke.Social_Assistance_Fund.exceptions;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, Object identifier) {
        super(resourceName + " already exists with identifier: " + identifier);
    }
}
