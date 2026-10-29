package hospital.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Doctor management: profile, specialization, department assignment,
 * availability and deactivation. Reuses the hr_department table so doctors and
 * staff share one department list.
 */
public final class DoctorService {

    private DoctorService() {
    }

    public static final class Doctor {
        public int doctorId;
        public String doctorCode = "";
        public String name = "";
        public String specialization = "";
        public Integer departmentId;
        public String departmentName = "";
        public String qualification = "";
        public int experienceYears;
        public String phone = "";
        public String email = "";
        public String consultationFee = "0";
        public String availableDays = "";
        public String availableFrom = "";
        public String availableTo = "";
        public String status = "Active";

        @Override
        public String toString() {
            if (name == null || name.isEmpty()) return "";
            if (specialization != null && !specialization.trim().isEmpty()) {
                return name + " (" + specialization + ")";
            }
            return name;
        }
    }

    public static int addDoctor(Doctor d) {
        Rbac.require("DOCTOR_MANAGE");
        validate(d);
        String sql = "INSERT INTO doctor (doctor_code, name, specialization, department_id, qualification, " +
                "experience_yrs, phone, email, consultation_fee, available_days, available_from, available_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, d);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw wrap(e, "Doctor code '" + d.doctorCode + "' already exists.");
        }
    }

    public static void updateDoctor(Doctor d) {
        Rbac.require("DOCTOR_MANAGE");
        validate(d);
        if (d.doctorId <= 0) {
            throw new IllegalArgumentException("Select a doctor to update.");
        }
        String sql = "UPDATE doctor SET doctor_code=?, name=?, specialization=?, department_id=?, " +
                "qualification=?, experience_yrs=?, phone=?, email=?, consultation_fee=?, " +
                "available_days=?, available_from=?, available_to=?, status=? WHERE doctor_id=?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, d);
            ps.setInt(14, d.doctorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e, "Doctor code '" + d.doctorCode + "' already exists.");
        }
    }

    /** Soft delete: keeps appointment history intact. */
    public static void deactivateDoctor(int doctorId) {
        Rbac.require("DOCTOR_MANAGE");
        setStatus(doctorId, "Inactive");
    }

    public static void activateDoctor(int doctorId) {
        Rbac.require("DOCTOR_MANAGE");
        setStatus(doctorId, "Active");
    }

    private static void setStatus(int doctorId, String status) {
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE doctor SET status = ? WHERE doctor_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, doctorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update doctor status: " + e.getMessage(), e);
        }
    }

    public static List<Doctor> list(String search, boolean includeInactive) {
        List<Doctor> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT d.doctor_id, d.doctor_code, d.name, d.specialization, d.department_id, " +
                        "dp.department_name, d.qualification, d.experience_yrs, d.phone, d.email, " +
                        "d.consultation_fee, d.available_days, d.available_from, d.available_to, d.status " +
                        "FROM doctor d LEFT JOIN hr_department dp ON d.department_id = dp.department_id ");
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where.add("(d.name LIKE ? OR d.doctor_code LIKE ? OR d.specialization LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!includeInactive) {
            where.add("d.status = 'Active'");
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }
        sql.append("ORDER BY d.name");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load doctors: " + e.getMessage(), e);
        }
        return list;
    }

    /** Active doctors who are available on the given weekday. */
    public static List<Doctor> availableOn(LocalDate date) {
        String day = date.getDayOfWeek().name();
        String dayShort = "" + day.charAt(0) + day.substring(1).toLowerCase();
        String sql = "SELECT d.doctor_id, d.doctor_code, d.name, d.specialization, d.department_id, " +
                "dp.department_name, d.qualification, d.experience_yrs, d.phone, d.email, " +
                "d.consultation_fee, d.available_days, d.available_from, d.available_to, d.status " +
                "FROM doctor d LEFT JOIN hr_department dp ON d.department_id = dp.department_id " +
                "WHERE d.status = 'Active' " +
                "AND (d.available_days IS NULL OR d.available_days = '' OR d.available_days LIKE ?) " +
                "ORDER BY d.name";
        List<Doctor> list = new ArrayList<>();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + dayShort + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load available doctors: " + e.getMessage(), e);
        }
        return list;
    }

    public static Doctor findById(int doctorId) {
        String sql = "SELECT d.doctor_id, d.doctor_code, d.name, d.specialization, d.department_id, " +
                "dp.department_name, d.qualification, d.experience_yrs, d.phone, d.email, " +
                "d.consultation_fee, d.available_days, d.available_from, d.available_to, d.status " +
                "FROM doctor d LEFT JOIN hr_department dp ON d.department_id = dp.department_id " +
                "WHERE d.doctor_id = ?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load doctor: " + e.getMessage(), e);
        }
    }

    // --------------------------------------------------------------- helpers

    private static void bind(PreparedStatement ps, Doctor d) throws SQLException {
        ps.setString(1, d.doctorCode.trim());
        ps.setString(2, d.name.trim());
        ps.setString(3, trim(d.specialization));
        if (d.departmentId == null) {
            ps.setNull(4, java.sql.Types.INTEGER);
        } else {
            ps.setInt(4, d.departmentId);
        }
        ps.setString(5, trim(d.qualification));
        ps.setInt(6, d.experienceYears);
        ps.setString(7, trim(d.phone));
        ps.setString(8, trim(d.email));
        java.math.BigDecimal fee;
        try {
            fee = new java.math.BigDecimal(d.consultationFee == null || d.consultationFee.trim().isEmpty()
                    ? "0" : d.consultationFee.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Consultation fee must be a number.");
        }
        ps.setBigDecimal(9, fee);
        ps.setString(10, trim(d.availableDays));
        ps.setString(11, trim(d.availableFrom));
        ps.setString(12, trim(d.availableTo));
        ps.setString(13, d.status == null ? "Active" : d.status);
    }

    private static Doctor map(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.doctorId = rs.getInt("doctor_id");
        d.doctorCode = rs.getString("doctor_code");
        d.name = rs.getString("name");
        d.specialization = rs.getString("specialization");
        int deptId = rs.getInt("department_id");
        d.departmentId = rs.wasNull() ? null : deptId;
        d.departmentName = rs.getString("department_name");
        d.qualification = rs.getString("qualification");
        d.experienceYears = rs.getInt("experience_yrs");
        d.phone = rs.getString("phone");
        d.email = rs.getString("email");
        d.consultationFee = rs.getString("consultation_fee");
        d.availableDays = rs.getString("available_days");
        d.availableFrom = rs.getString("available_from");
        d.availableTo = rs.getString("available_to");
        d.status = rs.getString("status");
        return d;
    }

    private static void validate(Doctor d) {
        if (d.doctorCode == null || d.doctorCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Doctor code is required.");
        }
        if (d.name == null || d.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Doctor name is required.");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static RuntimeException wrap(SQLException e, String duplicateMessage) {
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
            return new IllegalStateException(duplicateMessage);
        }
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
