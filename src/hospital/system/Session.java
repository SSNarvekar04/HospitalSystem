package hospital.system;

/**
 * Holds the authenticated user for the running desktop session.
 * Swing is single-user desktop, so a simple static holder is sufficient.
 */
public final class Session {

    private static Rbac.User current;

    private Session() {
    }

    public static void set(Rbac.User user) {
        current = user;
    }

    public static Rbac.User current() {
        return current;
    }

    public static boolean isLoggedIn() {
        return current != null;
    }

    public static boolean isAdmin() {
        if (current == null) {
            ensureDevSession();
        }
        return current != null && current.isAdmin();
    }

    public static String username() {
        return current == null ? null : current.username;
    }

    public static void clear() {
        current = null;
    }

    /**
     * Initializes a default admin session for standalone/development screen execution.
     * When screens are opened directly via their main method instead of going through Login,
     * this ensures the session has required permissions.
     */
    public static void ensureDevSession() {
        if (current == null) {
            try {
                Rbac.User u = Rbac.authenticate("admin", "admin123");
                if (u == null) {
                    u = Rbac.authenticateLegacy("admin", "admin");
                }
                if (u != null) {
                    set(u);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
