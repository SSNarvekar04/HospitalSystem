package hospital.system;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Patient registration.
 *
 * Reuses the existing {@code patient_info1} table and writes the new optional
 * columns added by extended_migration.sql. Every patient gets a unique
 * generated code (PT-000001) and obvious duplicates (same id_type + number, or
 * same name + contact) are rejected before insert.
 */
public final class PatientService {

    private PatientService() {
    }

    public static final class Patient {
        public int id;
        public String patientCode = "";
        public String idType = "Aadhar Card";
        public String number = "";
        public String name = "";
        public String gender = "";
        public String disease = "";
        public String dateOfBirth = "";
        public String age = "";
        public String contact = "";
        public String address = "";
        public String emergencyContact = "";
        public String bloodGroup = "";
        public String patientType = "OPD";
        public String status = "Active";

        @Override
        public String toString() {
            if (name == null || name.isEmpty()) return "";
            if (patientCode != null && !patientCode.isEmpty()) {
                return patientCode + " - " + name;
            }
            return name;
        }
    }

    public static int register(Patient p) {
        Rbac.require("PATIENT_MANAGE");
        validate(p);
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                Integer dup = findDuplicate(c, p);
                if (dup != null) {
                    throw new IllegalStateException(
                            "A patient with the same ID number or name/contact already exists (id " + dup + ").");
                }
                String code = nextCode(c);
                String sql = "INSERT INTO patient_info1 (id_type, number, name, gender, disease, id_number, " +
                        "patient_code, date_of_birth, age, contact, address, emergency_contact, blood_group, " +
                        "patient_type, registration_date, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'Active')";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps, p, code);
                    ps.executeUpdate();
                    int id = lastId(ps);
                    c.commit();
                    return id;
                }
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not register patient: " + e.getMessage(), e);
        }
    }

    public static void update(Patient p) {
        Rbac.require("PATIENT_MANAGE");
        validate(p);
        if (p.id <= 0) {
            throw new IllegalArgumentException("Select a patient to update.");
        }
        String sql = "UPDATE patient_info1 SET id_type=?, number=?, name=?, gender=?, disease=?, " +
                "date_of_birth=?, age=?, contact=?, address=?, emergency_contact=?, blood_group=?, " +
                "patient_type=?, status=? WHERE id=?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.idType);
            ps.setString(2, trim(p.number));
            ps.setString(3, p.name.trim());
            ps.setString(4, trim(p.gender));
            ps.setString(5, trim(p.disease));
            setDate(ps, 6, p.dateOfBirth);
            if (p.age == null || p.age.trim().isEmpty()) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, Integer.parseInt(p.age.trim()));
            }
            ps.setString(8, trim(p.contact));
            ps.setString(9, trim(p.address));
            ps.setString(10, trim(p.emergencyContact));
            ps.setString(11, trim(p.bloodGroup));
            ps.setString(12, p.patientType == null ? "OPD" : p.patientType);
            ps.setString(13, p.status == null ? "Active" : p.status);
            ps.setInt(14, p.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update patient: " + e.getMessage(), e);
        }
    }

    public static Patient findById(int id) {
        String sql = "SELECT id, patient_code, id_type, number, name, gender, disease, date_of_birth, " +
                "age, contact, address, emergency_contact, blood_group, patient_type, status " +
                "FROM patient_info1 WHERE id = ?";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load patient: " + e.getMessage(), e);
        }
    }

    public static List<Patient> list(String search, boolean includeInactive) {
        List<Patient> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, patient_code, id_type, number, name, gender, disease, date_of_birth, " +
                        "age, contact, address, emergency_contact, blood_group, patient_type, status " +
                        "FROM patient_info1 ");
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where.add("(name LIKE ? OR patient_code LIKE ? OR contact LIKE ? OR number LIKE ?)");
            String like = "%" + search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (!includeInactive) {
            where.add("(status IS NULL OR status = 'Active')");
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }
        sql.append("ORDER BY id DESC");
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
            throw new IllegalStateException("Could not load patients: " + e.getMessage(), e);
        }
        return list;
    }

    // --------------------------------------------------------------- helpers

    private static String nextCode(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(patient_code, 4) AS UNSIGNED)), 0) FROM patient_info1 " +
                        "WHERE patient_code LIKE 'PT-%'");
             ResultSet rs = ps.executeQuery()) {
            int max = rs.next() ? rs.getInt(1) : 0;
            return String.format("PT-%06d", max + 1);
        }
    }

    private static Integer findDuplicate(Connection c, Patient p) throws SQLException {
        if (p.id > 0) {
            return null;
        }
        if (p.number != null && !p.number.trim().isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM patient_info1 WHERE id_type = ? AND number = ? LIMIT 1")) {
                ps.setString(1, p.idType);
                ps.setString(2, p.number.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        if (p.name != null && !p.name.trim().isEmpty()
                && p.contact != null && !p.contact.trim().isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM patient_info1 WHERE name = ? AND contact = ? LIMIT 1")) {
                ps.setString(1, p.name.trim());
                ps.setString(2, p.contact.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    private static void bind(PreparedStatement ps, Patient p, String code) throws SQLException {
        ps.setString(1, p.idType);
        ps.setString(2, trim(p.number));
        ps.setString(3, p.name.trim());
        ps.setString(4, trim(p.gender));
        ps.setString(5, trim(p.disease));
        ps.setString(6, trim(p.number));
        ps.setString(7, code);
        setDate(ps, 8, p.dateOfBirth);
        if (p.age == null || p.age.trim().isEmpty()) {
            ps.setNull(9, Types.INTEGER);
        } else {
            ps.setInt(9, Integer.parseInt(p.age.trim()));
        }
        ps.setString(10, trim(p.contact));
        ps.setString(11, trim(p.address));
        ps.setString(12, trim(p.emergencyContact));
        ps.setString(13, trim(p.bloodGroup));
        ps.setString(14, p.patientType == null ? "OPD" : p.patientType);
    }

    private static Patient map(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.id = rs.getInt("id");
        p.patientCode = rs.getString("patient_code");
        p.idType = rs.getString("id_type");
        p.number = rs.getString("number");
        p.name = rs.getString("name");
        p.gender = rs.getString("gender");
        p.disease = rs.getString("disease");
        Date dob = rs.getDate("date_of_birth");
        p.dateOfBirth = dob == null ? "" : dob.toString();
        int age = rs.getInt("age");
        p.age = rs.wasNull() ? "" : String.valueOf(age);
        p.contact = rs.getString("contact");
        p.address = rs.getString("address");
        p.emergencyContact = rs.getString("emergency_contact");
        p.bloodGroup = rs.getString("blood_group");
        p.patientType = rs.getString("patient_type");
        p.status = rs.getString("status");
        return p;
    }

    private static void setDate(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            ps.setNull(idx, Types.DATE);
            return;
        }
        try {
            ps.setDate(idx, Date.valueOf(LocalDate.parse(value.trim())));
        } catch (Exception e) {
            throw new IllegalArgumentException("Date of birth must be yyyy-MM-dd.");
        }
    }

    private static int lastId(Statement s) throws SQLException {
        try (ResultSet rs = s.getGeneratedKeys()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static void validate(Patient p) {
        if (p.name == null || p.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name is required.");
        }
        if (p.idType == null || p.idType.trim().isEmpty()) {
            throw new IllegalArgumentException("ID type is required.");
        }
        if (p.age != null && !p.age.trim().isEmpty()) {
            try {
                int a = Integer.parseInt(p.age.trim());
                if (a < 0 || a > 150) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Age must be a number between 0 and 150.");
            }
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
