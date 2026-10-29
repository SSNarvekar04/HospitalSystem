package hospital.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultation & clinical records.
 *
 * Records vitals, symptoms, diagnosis and treatment plan, and links the
 * consultation to the patient and (where present) the appointment. A
 * consultation is completed and appointment status is set to 'Completed' in the
 * same transaction. Prescriptions are stored per consultation.
 *
 * This module stores what a clinician enters; it never generates a diagnosis.
 */
public final class ConsultationService {

    private ConsultationService() {
    }

    public static final class Consultation {
        public int consultationId;
        public Integer appointmentId;
        public Integer patientId;
        public String patientName = "";
        public Integer doctorId;
        public String doctorName = "";
        public String date = "";
        public String bloodPressure = "";
        public String temperature = "";
        public String pulse = "";
        public String weight = "";
        public String symptoms = "";
        public String diagnosis = "";
        public String treatmentPlan = "";
        public String notes = "";
    }

    public static final class Medicine {
        public String name = "";
        public String dosage = "";
        public String frequency = "";
        public String duration = "";
        public String instructions = "";
    }

    /** Saves a consultation together with its prescription lines (transactional). */
    public static int save(Consultation c, List<Medicine> medicines) {
        Rbac.require("CONSULT_MANAGE");
        if (c.patientName == null || c.patientName.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name is required for a consultation.");
        }
        try (Connection con = new connection().connection) {
            con.setAutoCommit(false);
            try {
                String sql = "INSERT INTO consultation (appointment_id, patient_id, patient_name, doctor_id, " +
                        "doctor_name, consultation_date, blood_pressure, temperature, pulse, weight, symptoms, " +
                        "diagnosis, treatment_plan, notes, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                int consultationId;
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    setInt(ps, 1, c.appointmentId);
                    setInt(ps, 2, c.patientId);
                    ps.setString(3, c.patientName.trim());
                    setInt(ps, 4, c.doctorId);
                    ps.setString(5, c.doctorName == null ? "" : c.doctorName.trim());
                    ps.setString(6, trim(c.bloodPressure));
                    ps.setString(7, trim(c.temperature));
                    ps.setString(8, trim(c.pulse));
                    ps.setString(9, trim(c.weight));
                    ps.setString(10, trim(c.symptoms));
                    ps.setString(11, trim(c.diagnosis));
                    ps.setString(12, trim(c.treatmentPlan));
                    ps.setString(13, trim(c.notes));
                    ps.setString(14, Session.username() == null ? "system" : Session.username());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        consultationId = rs.next() ? rs.getInt(1) : -1;
                    }
                }
                if (medicines != null) {
                    for (Medicine m : medicines) {
                        if (m.name == null || m.name.trim().isEmpty()) {
                            continue;
                        }
                        try (PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO prescription (consultation_id, medicine_name, dosage, frequency, " +
                                        "duration, instructions) VALUES (?, ?, ?, ?, ?, ?)")) {
                            ps.setInt(1, consultationId);
                            ps.setString(2, m.name.trim());
                            ps.setString(3, trim(m.dosage));
                            ps.setString(4, trim(m.frequency));
                            ps.setString(5, trim(m.duration));
                            ps.setString(6, trim(m.instructions));
                            ps.executeUpdate();
                        }
                    }
                }
                if (c.appointmentId != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE bookappointment2 SET status = 'Completed' WHERE appointment_id = ?")) {
                        ps.setInt(1, c.appointmentId);
                        ps.executeUpdate();
                    }
                }
                con.commit();
                return consultationId;
            } catch (RuntimeException | SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save consultation: " + e.getMessage(), e);
        }
    }

    public static List<Consultation> history(String search, Integer patientId) {
        List<Consultation> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT consultation_id, appointment_id, patient_id, patient_name, doctor_id, doctor_name, " +
                        "consultation_date, blood_pressure, temperature, pulse, weight, symptoms, diagnosis, " +
                        "treatment_plan, notes FROM consultation ");
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (patientId != null) {
            where.add("patient_id = ?");
            params.add(patientId);
        }
        if (search != null && !search.trim().isEmpty()) {
            where.add("(patient_name LIKE ? OR doctor_name LIKE ? OR diagnosis LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }
        sql.append("ORDER BY consultation_date DESC, consultation_id DESC");
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
            throw new IllegalStateException("Could not load consultations: " + e.getMessage(), e);
        }
        return list;
    }

    public static List<Medicine> prescriptions(int consultationId) {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT medicine_name, dosage, frequency, duration, instructions FROM prescription " +
                "WHERE consultation_id = ? ORDER BY prescription_id";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, consultationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Medicine m = new Medicine();
                    m.name = rs.getString(1);
                    m.dosage = rs.getString(2);
                    m.frequency = rs.getString(3);
                    m.duration = rs.getString(4);
                    m.instructions = rs.getString(5);
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load prescriptions: " + e.getMessage(), e);
        }
        return list;
    }

    // --------------------------------------------------------------- helpers

    private static Consultation map(ResultSet rs) throws SQLException {
        Consultation c = new Consultation();
        c.consultationId = rs.getInt("consultation_id");
        c.appointmentId = getInt(rs, "appointment_id");
        c.patientId = getInt(rs, "patient_id");
        c.patientName = rs.getString("patient_name");
        c.doctorId = getInt(rs, "doctor_id");
        c.doctorName = rs.getString("doctor_name");
        java.sql.Timestamp ts = rs.getTimestamp("consultation_date");
        c.date = ts == null ? "" : ts.toString();
        c.bloodPressure = rs.getString("blood_pressure");
        c.temperature = rs.getString("temperature");
        c.pulse = rs.getString("pulse");
        c.weight = rs.getString("weight");
        c.symptoms = rs.getString("symptoms");
        c.diagnosis = rs.getString("diagnosis");
        c.treatmentPlan = rs.getString("treatment_plan");
        c.notes = rs.getString("notes");
        return c;
    }

    private static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static void setInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
