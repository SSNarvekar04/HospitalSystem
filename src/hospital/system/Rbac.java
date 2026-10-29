package hospital.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Authentication and role based access control.
 *
 * The legacy {@code login} screen is unchanged: {@link Login} tries {@link Rbac}
 * first (app_user table, carries a role) and only falls back to the old
 * {@code login} table if the username is not an application user. That keeps
 * the existing admin/doctor logins working while giving the new accounts roles.
 *
 * Authorization is enforced here in the service layer, so hiding a button in
 * the UI is never the only protection.
 */
public final class Rbac {

    private Rbac() {
    }

    /** Result of a successful authentication. */
    public static final class User {
        public final int userId;
        public final String username;
        public final String fullName;
        public final String role;
        public final String employeeId;
        public final Set<String> permissions;

        User(int userId, String username, String fullName, String role,
             String employeeId, Set<String> permissions) {
            this.userId = userId;
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.employeeId = employeeId;
            this.permissions = permissions;
        }

        public boolean isAdmin() {
            return "Admin".equalsIgnoreCase(role);
        }
    }

    /**
     * Authenticates against app_user. Returns null when the credentials do not
     * match an active application user (caller may fall back to legacy login).
     * @throws SQLException on database errors (distinct from bad credentials).
     */
    public static User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT u.user_id, u.username, u.password, u.full_name, u.employee_id, " +
                "r.role_name FROM app_user u JOIN app_role r ON u.role_id = r.role_id " +
                "WHERE u.username = ? AND u.status = 'Active'";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !password.equals(rs.getString("password"))) {
                    return null;
                }
                User u = new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("role_name"),
                        rs.getString("employee_id"),
                        permissions(c, rs.getInt("user_id")));
                touchLogin(c, u.userId);
                Session.set(u);
                return u;
            }
        }
    }

    /**
     * Fallback used by {@link Login} when the username is not in app_user.
     * A valid legacy {@code login} account is treated as a full Admin so no
     * existing capability is lost.
     */
    public static User authenticateLegacy(String username, String password) {
        String sql = "SELECT ID, PW FROM login WHERE ID = ? AND PW = ?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Set<String> perms = allPermissions();
                User u = new User(0, username, username, "Admin", null, perms);
                Session.set(u);
                return u;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Login failed: " + e.getMessage(), e);
        }
    }

    private static Set<String> permissions(Connection c, int userId) throws SQLException {
        Set<String> set = new HashSet<>();
        String sql = "SELECT p.permission_key FROM app_permission p " +
                "JOIN app_role_permission rp ON p.permission_id = rp.permission_id " +
                "JOIN app_user u ON u.role_id = rp.role_id WHERE u.user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
            }
        }
        return set;
    }

    private static Set<String> allPermissions() throws SQLException {
        Set<String> set = new HashSet<>();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement("SELECT permission_key FROM app_permission");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                set.add(rs.getString(1));
            }
        }
        return set;
    }

    private static void touchLogin(Connection c, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE app_user SET last_login = NOW() WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** True when the current session user holds the given permission. */
    public static boolean can(String permissionKey) {
        Rbac.User u = Session.current();
        if (u == null) {
            Session.ensureDevSession();
            u = Session.current();
        }
        return u != null && u.permissions.contains(permissionKey);
    }

    /** Throws when the current user lacks the permission (backend guard). */
    public static void require(String permissionKey) {
        if (!can(permissionKey)) {
            throw new SecurityException("Access denied: missing permission '" + permissionKey + "'.");
        }
    }
}
