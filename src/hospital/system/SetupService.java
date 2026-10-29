package hospital.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin & Hospital Setup: hospital profile, departments and user/role
 * management. All write operations validate input and enforce the matching
 * RBAC permission in the backend.
 */
public final class SetupService {

    private SetupService() {
    }

    // ---------------------------------------------------------------- profile

    public static final class Profile {
        public String hospitalName = "";
        public String address = "";
        public String city = "";
        public String phone = "";
        public String email = "";
        public String website = "";
        public String registrationNo = "";
        public String establishedYear = "";
    }

    public static Profile loadProfile() {
        Profile p = new Profile();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT hospital_name, address, city, phone, email, website, " +
                             "registration_no, established_year FROM hospital_profile WHERE profile_id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                p.hospitalName = nz(rs.getString(1));
                p.address = nz(rs.getString(2));
                p.city = nz(rs.getString(3));
                p.phone = nz(rs.getString(4));
                p.email = nz(rs.getString(5));
                p.website = nz(rs.getString(6));
                p.registrationNo = nz(rs.getString(7));
                p.establishedYear = nz(rs.getString(8));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load hospital profile: " + e.getMessage(), e);
        }
        return p;
    }

    public static void saveProfile(Profile p) {
        Rbac.require("SETUP_PROFILE");
        if (p.hospitalName == null || p.hospitalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital name is required.");
        }
        String sql = "INSERT INTO hospital_profile (profile_id, hospital_name, address, city, " +
                "phone, email, website, registration_no, established_year) " +
                "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE hospital_name=VALUES(hospital_name), address=VALUES(address), " +
                "city=VALUES(city), phone=VALUES(phone), email=VALUES(email), website=VALUES(website), " +
                "registration_no=VALUES(registration_no), established_year=VALUES(established_year)";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.hospitalName.trim());
            ps.setString(2, trim(p.address));
            ps.setString(3, trim(p.city));
            ps.setString(4, trim(p.phone));
            ps.setString(5, trim(p.email));
            ps.setString(6, trim(p.website));
            ps.setString(7, trim(p.registrationNo));
            ps.setString(8, trim(p.establishedYear));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save hospital profile: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ departments

    /** Departments reused by the HR module (hr_department). */
    public static void addDepartment(String name) {
        Rbac.require("SETUP_DEPARTMENT");
        name = requireName(name, "Department");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO hr_department (department_name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrapDuplicate(e, "Department '" + name + "' already exists.");
        }
    }

    public static void renameDepartment(int departmentId, String newName) {
        Rbac.require("SETUP_DEPARTMENT");
        newName = requireName(newName, "Department");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE hr_department SET department_name = ? WHERE department_id = ?")) {
            ps.setString(1, newName);
            ps.setInt(2, departmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrapDuplicate(e, "Department '" + newName + "' already exists.");
        }
    }

    public static void deleteDepartment(int departmentId) {
        Rbac.require("SETUP_DEPARTMENT");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM hr_department WHERE department_id = ?")) {
            ps.setInt(1, departmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key")) {
                throw new IllegalStateException(
                        "Department is in use by doctors or employees and cannot be deleted.");
            }
            throw new IllegalStateException("Could not delete department: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------- users/roles

    public static final class Role {
        public final int id;
        public final String name;

        public Role(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static List<Role> roles() {
        List<Role> list = new ArrayList<>();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT role_id, role_name FROM app_role ORDER BY role_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Role(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load roles: " + e.getMessage(), e);
        }
        return list;
    }

    public static int addUser(String username, String password, String fullName, int roleId) {
        Rbac.require("SETUP_USER");
        username = requireName(username, "Username");
        if (password == null || password.trim().length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        String sql = "INSERT INTO app_user (username, password, full_name, role_id, status) " +
                "VALUES (?, ?, ?, ?, 'Active')";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, trim(fullName));
            ps.setInt(4, roleId);
            ps.executeUpdate();
            return lastId(ps);
        } catch (SQLException e) {
            throw wrapDuplicate(e, "Username '" + username + "' already exists.");
        }
    }

    public static void updateUser(int userId, String fullName, int roleId, String status) {
        Rbac.require("SETUP_USER");
        if (Session.current() != null && Session.current().userId == userId
                && "Inactive".equalsIgnoreCase(status)) {
            throw new IllegalStateException("You cannot deactivate your own account.");
        }
        String sql = "UPDATE app_user SET full_name = ?, role_id = ?, status = ? WHERE user_id = ?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, trim(fullName));
            ps.setInt(2, roleId);
            ps.setString(3, status);
            ps.setInt(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update user: " + e.getMessage(), e);
        }
    }

    public static void resetPassword(int userId, String newPassword) {
        Rbac.require("SETUP_USER");
        if (newPassword == null || newPassword.trim().length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE app_user SET password = ? WHERE user_id = ?")) {
            ps.setString(1, newPassword.trim());
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reset password: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------- helpers

    private static int lastId(Statement s) throws SQLException {
        try (ResultSet rs = s.getGeneratedKeys()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String requireName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static RuntimeException wrapDuplicate(SQLException e, String message) {
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
            return new IllegalStateException(message);
        }
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
