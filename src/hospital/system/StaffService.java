package hospital.system;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hospital Staff & Shift Management.
 *
 * Covers the shift master, duty roster assignment and staff availability. The
 * roster rules are enforced in the database transaction as well:
 *   - a staff member cannot be on two shifts on the same date (unique key plus
 *     an explicit check that produces a friendly message);
 *   - a staff member cannot be rostered on a day covered by an approved leave.
 */
public final class StaffService {

    private StaffService() {
    }

    public static final class Shift {
        public final int id;
        public final String name;
        public final String start;
        public final String end;

        public Shift(int id, String name, String start, String end) {
            this.id = id;
            this.name = name;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return name + " (" + start + "-" + end + ")";
        }
    }

    public static List<Shift> shifts() {
        List<Shift> list = new ArrayList<>();
        String sql = "SELECT shift_id, shift_name, start_time, end_time FROM shift_master ORDER BY shift_id";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Shift(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load shifts: " + e.getMessage(), e);
        }
        return list;
    }

    public static int addShift(String name, String start, String end, String description) {
        Rbac.require("STAFF_MANAGE");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Shift name is required.");
        }
        if (start == null || start.trim().isEmpty() || end == null || end.trim().isEmpty()) {
            throw new IllegalArgumentException("Shift start and end times are required.");
        }
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shift_master (shift_name, start_time, end_time, description) " +
                             "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, start.trim());
            ps.setString(3, end.trim());
            ps.setString(4, description == null ? "" : description.trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                throw new IllegalStateException("Shift '" + name + "' already exists.");
            }
            throw new IllegalStateException("Could not add shift: " + e.getMessage(), e);
        }
    }

    /**
     * Assigns a staff member to a shift on a date. Rejects a second shift on
     * the same day and clashes with approved leave.
     */
    public static void assignShift(String employeeId, int shiftId, LocalDate date) {
        Rbac.require("STAFF_MANAGE");
        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Select a staff member.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Select a roster date.");
        }
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                if (hasRoster(c, employeeId, date)) {
                    throw new IllegalStateException(
                            "This staff member already has a shift on " + date + ".");
                }
                if (hasApprovedLeave(c, employeeId, date)) {
                    throw new IllegalStateException(
                            "This staff member is on approved leave on " + date + ".");
                }
                Integer deptId = departmentOf(c, employeeId);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO duty_roster (employee_id, shift_id, roster_date, department_id, status) " +
                                "VALUES (?, ?, ?, ?, 'Scheduled')")) {
                    ps.setString(1, employeeId);
                    ps.setInt(2, shiftId);
                    ps.setDate(3, Date.valueOf(date));
                    if (deptId == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, deptId);
                    }
                    ps.executeUpdate();
                }
                c.commit();
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not assign shift: " + e.getMessage(), e);
        }
    }

    public static void cancelRoster(int rosterId) {
        Rbac.require("STAFF_MANAGE");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM duty_roster WHERE roster_id = ?")) {
            ps.setInt(1, rosterId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not cancel roster entry: " + e.getMessage(), e);
        }
    }

    /**
     * Staff available for a given date and (optionally) shift: active staff who
     * are neither already rostered on that date nor on approved leave.
     */
    public static List<String> availableStaff(LocalDate date, Integer shiftId) {
        List<String> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT e.employee_id, e.name FROM employee e " +
                        "WHERE e.status = 'Active' " +
                        "AND NOT EXISTS (SELECT 1 FROM duty_roster r WHERE r.employee_id = e.employee_id " +
                        "AND r.roster_date = ?) " +
                        "AND NOT EXISTS (SELECT 1 FROM employee_leave l WHERE l.employee_id = e.employee_id " +
                        "AND l.status = 'Approved' AND ? BETWEEN l.start_date AND l.end_date) ");
        if (shiftId != null) {
            sql.append("AND NOT EXISTS (SELECT 1 FROM duty_roster r2 WHERE r2.employee_id = e.employee_id " +
                    "AND r2.roster_date = ? AND r2.shift_id = ?) ");
        }
        sql.append("ORDER BY e.name");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setDate(i++, Date.valueOf(date));
            ps.setDate(i++, Date.valueOf(date));
            if (shiftId != null) {
                ps.setDate(i++, Date.valueOf(date));
                ps.setInt(i++, shiftId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1) + " - " + rs.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load available staff: " + e.getMessage(), e);
        }
        return list;
    }

    // --------------------------------------------------------------- helpers

    private static boolean hasRoster(Connection c, String employeeId, LocalDate date) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM duty_roster WHERE employee_id = ? AND roster_date = ?")) {
            ps.setString(1, employeeId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasApprovedLeave(Connection c, String employeeId, LocalDate date) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM employee_leave WHERE employee_id = ? AND status = 'Approved' " +
                        "AND ? BETWEEN start_date AND end_date")) {
            ps.setString(1, employeeId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Integer departmentOf(Connection c, String employeeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT department_id FROM employee WHERE employee_id = ?")) {
            ps.setString(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }
}
