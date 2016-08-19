public enum AuthenticationStatus {

    PASS("pass"),
    INVALID("invalid"),
    INVALID_TO_BLOCKED("invalidToBlocked"),
    BLOCKED("blocked"),
    NO_SUCH_USER("noSuchUser"),
    UNKOWN("unknown");

    private final String authStatus;

    AuthenticationStatus(String authStatus) {
        this.authStatus = authStatus;
    }

    public static AuthenticationStatus getAuthStatusFromStr(String statusStr) {
        for (AuthenticationStatus authStatus : AuthenticationStatus.values()) {
            if (authStatus.authStatus.equals(statusStr)) {
                return authStatus;
            }
        }
        return UNKOWN;
    }

    @Override
    public String toString() {
        return this.authStatus;
    }

}
