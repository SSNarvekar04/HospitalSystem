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
 * Appointment & queue management.
 *
 * Builds on the existing {@code bookappointment2} table (additive columns from
 * extended_migration.sql). Reuses the original flow: patient + doctor + time,
 * status starts as 'Scheduled'. New behaviour added here:
 *   - doctor availability checked before booking;
 *   - free slots derived from the doctor's working hours minus booked times;
 *   - overlapping appointment for the same doctor/time/date rejected;
 *   - check-in assigns a queue number; queue is per doctor/date.
 */
public final class AppointmentService {

    private AppointmentService() {
    }

    /** Default slot size in minutes used when generating availability. */
    private static final int SLOT_MINUTES = 30;

    public static int book(String patientName, Integer patientId, String patientType,
                           DoctorService.Doctor doctor, String time, LocalDate date, String notes) {
        Rbac.require("APPOINTMENT_MANAGE");
        if (patientName == null || patientName.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name is required.");
        }
        if (doctor == null) {
            throw new IllegalArgumentException("Select a doctor.");
        }
        if (!"Active".equalsIgnoreCase(doctor.status)) {
            throw new IllegalStateException("Dr. " + doctor.name + " is not available for appointments.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Appointment date is required.");
        }
        if (!isDoctorAvailableOnDate(doctor, date)) {
            String dayName = date.getDayOfWeek().name();
            dayName = dayName.charAt(0) + dayName.substring(1).toLowerCase();
            throw new IllegalStateException("Dr. " + doctor.name + " is not available on " + dayName + " (" + date + "). Available days: " + doctor.availableDays);
        }
        if (time == null || time.trim().isEmpty()) {
            throw new IllegalArgumentException("Appointment time is required (HH:mm).");
        }
        String t = time.trim();
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                if (overlaps(c, doctor.doctorId, date, t)) {
                    throw new IllegalStateException(
                            "Dr. " + doctor.name + " already has an appointment at " + t + " on " + date + ".");
                }
                String sql = "INSERT INTO bookappointment2 (patient_name, patient_type, doctor_name, " +
                        "appointment_date, appointment_time, status, patient_id, doctor_id, notes) " +
                        "VALUES (?, ?, ?, ?, ?, 'Scheduled', ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, patientName.trim());
                    ps.setString(2, patientType == null || patientType.isEmpty() ? "Regular" : patientType);
                    ps.setString(3, doctor.name);
                    ps.setTimestamp(4, java.sql.Timestamp.valueOf(date.atStartOfDay()));
                    ps.setString(5, t);
                    if (patientId == null) {
                        ps.setNull(6, Types.INTEGER);
                    } else {
                        ps.setInt(6, patientId);
                    }
                    ps.setInt(7, doctor.doctorId);
                    ps.setString(8, notes == null ? "" : notes.trim());
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
            throw new IllegalStateException("Could not book appointment: " + e.getMessage(), e);
        }
    }

    /** Free 30-minute slots for a doctor on a date within their working hours. */
    public static List<String> availableSlots(DoctorService.Doctor doctor, LocalDate date) {
        List<String> slots = new ArrayList<>();
        if (doctor == null || date == null) {
            return slots;
        }
        if (!isDoctorAvailableOnDate(doctor, date)) {
            return slots;
        }
        String from = (doctor.availableFrom != null && !doctor.availableFrom.trim().isEmpty())
                ? doctor.availableFrom.trim() : "09:00";
        String to = (doctor.availableTo != null && !doctor.availableTo.trim().isEmpty())
                ? doctor.availableTo.trim() : "17:00";
        int start = toMinutes(from);
        int end = toMinutes(to);
        if (start < 0 || end < 0 || end <= start) {
            start = toMinutes("09:00");
            end = toMinutes("17:00");
        }
        List<String> booked = bookedTimes(doctor.doctorId, date);
        for (int m = start; m + SLOT_MINUTES <= end; m += SLOT_MINUTES) {
            String slot = fromMinutes(m);
            if (!booked.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Checks whether a doctor is available on the specified date based on doctor's available days. */
    public static boolean isDoctorAvailableOnDate(DoctorService.Doctor doctor, LocalDate date) {
        if (doctor == null || date == null) {
            return false;
        }
        if (doctor.availableDays == null || doctor.availableDays.trim().isEmpty()) {
            return true;
        }
        String daysStr = doctor.availableDays.trim().toLowerCase();
        if (daysStr.contains("all") || daysStr.contains("daily") || daysStr.contains("everyday")) {
            return true;
        }
        java.time.DayOfWeek dow = date.getDayOfWeek();
        String fullDay = dow.name().toLowerCase();
        String shortDay = fullDay.substring(0, 3);

        if (daysStr.contains("-")) {
            String[] range = daysStr.split("-");
            if (range.length == 2) {
                int startDow = parseDayOfWeekNumber(range[0].trim());
                int endDow = parseDayOfWeekNumber(range[1].trim());
                int currentDow = dow.getValue();
                if (startDow > 0 && endDow > 0) {
                    if (startDow <= endDow && currentDow >= startDow && currentDow <= endDow) {
                        return true;
                    } else if (startDow > endDow && (currentDow >= startDow || currentDow <= endDow)) {
                        return true;
                    }
                }
            }
        }

        String[] tokens = daysStr.split("[,;/\\s]+");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (fullDay.startsWith(t) || t.startsWith(shortDay)) {
                return true;
            }
        }
        return false;
    }

    private static int parseDayOfWeekNumber(String s) {
        s = s.toLowerCase();
        if (s.startsWith("mon")) return 1;
        if (s.startsWith("tue")) return 2;
        if (s.startsWith("wed")) return 3;
        if (s.startsWith("thu")) return 4;
        if (s.startsWith("fri")) return 5;
        if (s.startsWith("sat")) return 6;
        if (s.startsWith("sun")) return 7;
        return 0;
    }

    public static void reschedule(int appointmentId, LocalDate date, String time) {
        Rbac.require("APPOINTMENT_MANAGE");
        if (time == null || time.trim().isEmpty()) {
            throw new IllegalArgumentException("New time is required.");
        }
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                int doctorId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT doctor_id FROM bookappointment2 WHERE appointment_id = ?")) {
                    ps.setInt(1, appointmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Appointment not found.");
                        }
                        doctorId = rs.getInt(1);
                    }
                }
                if (overlapsExcept(c, doctorId, date, time.trim(), appointmentId)) {
                    throw new IllegalStateException("That slot is already taken for this doctor.");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookappointment2 SET appointment_date = ?, appointment_time = ?, " +
                                "status = 'Scheduled' WHERE appointment_id = ?")) {
                    ps.setTimestamp(1, java.sql.Timestamp.valueOf(date.atStartOfDay()));
                    ps.setString(2, time.trim());
                    ps.setInt(3, appointmentId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reschedule: " + e.getMessage(), e);
        }
    }

    public static void setStatus(int appointmentId, String status) {
        Rbac.require("APPOINTMENT_MANAGE");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE bookappointment2 SET status = ? WHERE appointment_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update appointment: " + e.getMessage(), e);
        }
    }

    public static void cancel(int appointmentId) {
        setStatus(appointmentId, "Cancelled");
    }

    public static void confirm(int appointmentId) {
        setStatus(appointmentId, "Confirmed");
    }

    /** Check in a patient: marks Checked-In and assigns the next queue number. */
    public static int checkIn(int appointmentId) {
        Rbac.require("APPOINTMENT_MANAGE");
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                int doctorId;
                LocalDate day;
                String status;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT doctor_id, appointment_date, status FROM bookappointment2 " +
                                "WHERE appointment_id = ?")) {
                    ps.setInt(1, appointmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Appointment not found.");
                        }
                        doctorId = rs.getInt(1);
                        java.sql.Timestamp ts = rs.getTimestamp(2);
                        day = ts == null ? LocalDate.now() : ts.toLocalDateTime().toLocalDate();
                        status = rs.getString(3);
                    }
                }
                if ("Completed".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status)) {
                    throw new IllegalStateException("Cannot check in a " + status + " appointment.");
                }
                int queue = nextQueueNo(c, doctorId, day);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookappointment2 SET status = 'Checked-In', queue_no = ?, checkin_time = NOW() " +
                                "WHERE appointment_id = ?")) {
                    ps.setInt(1, queue);
                    ps.setInt(2, appointmentId);
                    ps.executeUpdate();
                }
                c.commit();
                return queue;
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check in: " + e.getMessage(), e);
        }
    }

    // --------------------------------------------------------------- helpers

    private static boolean overlaps(Connection c, int doctorId, LocalDate date, String time) throws SQLException {
        return overlapsExcept(c, doctorId, date, time, -1);
    }

    private static boolean overlapsExcept(Connection c, int doctorId, LocalDate date,
                                          String time, int exceptId) throws SQLException {
        String sql = "SELECT 1 FROM bookappointment2 WHERE doctor_id = ? AND DATE(appointment_date) = ? " +
                "AND appointment_time = ? AND status <> 'Cancelled' AND appointment_id <> ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, time);
            ps.setInt(4, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<String> bookedTimes(int doctorId, LocalDate date) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT appointment_time FROM bookappointment2 WHERE doctor_id = ? " +
                "AND DATE(appointment_date) = ? AND status <> 'Cancelled'";
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load booked slots: " + e.getMessage(), e);
        }
        return list;
    }

    private static int nextQueueNo(Connection c, int doctorId, LocalDate day) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(queue_no), 0) FROM bookappointment2 " +
                        "WHERE doctor_id = ? AND DATE(appointment_date) = ?")) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                return (rs.next() ? rs.getInt(1) : 0) + 1;
            }
        }
    }

    private static int toMinutes(String hhmm) {
        try {
            String[] parts = hhmm.trim().split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String fromMinutes(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    private static int lastId(Statement s) throws SQLException {
        try (ResultSet rs = s.getGeneratedKeys()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
