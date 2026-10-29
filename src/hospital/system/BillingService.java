package hospital.system;

import java.math.BigDecimal;
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
 * Billing & payments.
 *
 * Reuses the existing {@code bill} table (extended with subtotal/discount/total/
 * status/patient_id/bill_number) and the {@code finance_payment} table created
 * by the Finance ERP module. A bill here is a superset of the simple bill the
 * existing {@code GenerateBill} screen writes, and keeps {@code amount} equal to
 * the final total so the Finance reporting continues to reconcile.
 *
 * Bill creation and payment recording are transactional.
 */
public final class BillingService {

    private BillingService() {
    }

    public static final class Line {
        public String chargeType;
        public String description;
        public BigDecimal amount = BigDecimal.ZERO;

        public Line(String chargeType, String description, BigDecimal amount) {
            this.chargeType = chargeType;
            this.description = description;
            this.amount = amount;
        }
    }

    public static final class Bill {
        public int billId;
        public String billNumber = "";
        public Integer patientId;
        public String patientName = "";
        public String doctorName = "";
        public String patientType = "OPD";
        public BigDecimal subtotal = BigDecimal.ZERO;
        public BigDecimal discount = BigDecimal.ZERO;
        public BigDecimal total = BigDecimal.ZERO;
        public BigDecimal paid = BigDecimal.ZERO;
        public BigDecimal balance = BigDecimal.ZERO;
        public String status = "Pending";
        public String dateTime = "";
        public Integer appointmentId;
    }

    public static final class Payment {
        public int paymentId;
        public int billId;
        public BigDecimal amount;
        public LocalDate date;
        public String method;
        public String reference;
        public String remarks;
    }

    /**
     * Creates a bill with its line items. All amounts are derived from the line
     * items minus discount; the total is stored in both {@code total} and the
     * legacy {@code amount} column.
     */
    public static int createBill(Integer patientId, String patientName, String doctorName,
                                 String patientType, Integer appointmentId,
                                 List<Line> lines, BigDecimal discount) {
        Rbac.require("BILLING_MANAGE");
        if (patientName == null || patientName.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name is required.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one charge line.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Line l : lines) {
            if (l.amount == null || l.amount.signum() < 0) {
                throw new IllegalArgumentException("Charge amounts cannot be negative.");
            }
            subtotal = subtotal.add(l.amount);
        }
        BigDecimal disc = discount == null ? BigDecimal.ZERO : discount;
        if (disc.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot be negative.");
        }
        if (disc.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed the subtotal.");
        }
        BigDecimal total = subtotal.subtract(disc);

        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                String billNumber = nextBillNumber(c);
                String sql = "INSERT INTO bill (patient_name, doctor_name, patient_type, amount, date_time, " +
                        "bill_number, patient_id, appointment_id, subtotal, discount, total, status, created_by) " +
                        "VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, 'Pending', ?)";
                int billId;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, patientName.trim());
                    ps.setString(2, trim(doctorName));
                    ps.setString(3, patientType == null ? "OPD" : patientType);
                    ps.setBigDecimal(4, total);
                    ps.setString(5, billNumber);
                    setInt(ps, 6, patientId);
                    setInt(ps, 7, appointmentId);
                    ps.setBigDecimal(8, subtotal);
                    ps.setBigDecimal(9, disc);
                    ps.setBigDecimal(10, total);
                    ps.setString(11, Session.username() == null ? "system" : Session.username());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        billId = rs.next() ? rs.getInt(1) : -1;
                    }
                }
                for (Line l : lines) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO bill_item (bill_id, charge_type, description, amount) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, billId);
                        ps.setString(2, l.chargeType == null || l.chargeType.isEmpty() ? "Other" : l.chargeType);
                        ps.setString(3, trim(l.description));
                        ps.setBigDecimal(4, l.amount);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return billId;
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create bill: " + e.getMessage(), e);
        }
    }

    /**
     * Records a payment for a bill and refreshes the bill status. Rejects
     * over-payment, duplicate references and duplicate same-bill/amount/date.
     */
    public static void recordPayment(int billId, BigDecimal amount, LocalDate date,
                                     String method, String reference, String remarks) {
        Rbac.require("BILLING_MANAGE");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Payment date is required.");
        }
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required.");
        }
        try (Connection c = new connection().connection) {
            c.setAutoCommit(false);
            try {
                BigDecimal total;
                Integer patientId;
                String patientName;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT total, patient_id, patient_name FROM bill WHERE bill_id = ?")) {
                    ps.setInt(1, billId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Bill not found.");
                        }
                        total = rs.getBigDecimal(1);
                        int pid = rs.getInt(2);
                        patientId = rs.wasNull() ? null : pid;
                        patientName = rs.getString(3);
                    }
                }
                BigDecimal alreadyPaid = paidAmount(c, billId);
                BigDecimal remaining = total.subtract(alreadyPaid);
                if (amount.compareTo(remaining) > 0) {
                    throw new IllegalStateException(
                            "Payment exceeds the outstanding balance of " + remaining + ".");
                }
                String ref = reference == null ? null : reference.trim();
                if (ref != null && !ref.isEmpty() && referenceExists(c, ref)) {
                    throw new IllegalStateException(
                            "Duplicate payment entry: reference '" + ref + "' already exists.");
                }
                if (duplicatePayment(c, billId, amount, date)) {
                    throw new IllegalStateException(
                            "Duplicate payment entry: a payment for the same bill, amount and date already exists.");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO finance_payment (bill_id, patient_name, amount, payment_date, " +
                                "payment_method, reference, remarks, patient_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, billId);
                    ps.setString(2, patientName);
                    ps.setBigDecimal(3, amount);
                    ps.setDate(4, Date.valueOf(date));
                    ps.setString(5, method.trim());
                    if (ref == null || ref.isEmpty()) {
                        ps.setNull(6, Types.VARCHAR);
                    } else {
                        ps.setString(6, ref);
                    }
                    ps.setString(7, remarks == null ? "" : remarks.trim());
                    setInt(ps, 8, patientId);
                    ps.executeUpdate();
                }
                updateStatus(c, billId, total);
                c.commit();
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record payment: " + e.getMessage(), e);
        }
    }

    public static void refund(int billId, String reason) {
        Rbac.require("BILLING_MANAGE");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE bill SET status = 'Refunded' WHERE bill_id = ?")) {
            ps.setInt(1, billId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not refund bill: " + e.getMessage(), e);
        }
    }

    public static List<Bill> listBills(String search, String statusFilter) {
        List<Bill> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT b.bill_id, b.bill_number, b.patient_id, b.patient_name, b.doctor_name, b.patient_type, " +
                        "COALESCE(b.subtotal, b.amount) subtotal, COALESCE(b.discount, 0) discount, " +
                        "COALESCE(b.total, b.amount) total, b.status, b.date_time, b.appointment_id, " +
                        "(SELECT COALESCE(SUM(p.amount),0) FROM finance_payment p WHERE p.bill_id = b.bill_id) paid " +
                        "FROM bill b ");
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where.add("(b.patient_name LIKE ? OR b.bill_number LIKE ? OR b.doctor_name LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
            if ("Partially Paid".equalsIgnoreCase(statusFilter)) {
                where.add("b.status = 'Partially Paid'");
            } else {
                where.add("b.status = ?");
                params.add(statusFilter);
            }
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }
        sql.append("ORDER BY b.bill_id DESC");
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Bill b = new Bill();
                    b.billId = rs.getInt("bill_id");
                    b.billNumber = rs.getString("bill_number");
                    int pid = rs.getInt("patient_id");
                    b.patientId = rs.wasNull() ? null : pid;
                    b.patientName = rs.getString("patient_name");
                    b.doctorName = rs.getString("doctor_name");
                    b.patientType = rs.getString("patient_type");
                    b.subtotal = rs.getBigDecimal("subtotal");
                    b.discount = rs.getBigDecimal("discount");
                    b.total = rs.getBigDecimal("total");
                    b.paid = rs.getBigDecimal("paid");
                    b.balance = b.total.subtract(b.paid);
                    b.status = rs.getString("status");
                    java.sql.Timestamp ts = rs.getTimestamp("date_time");
                    b.dateTime = ts == null ? "" : ts.toString();
                    int aid = rs.getInt("appointment_id");
                    b.appointmentId = rs.wasNull() ? null : aid;
                    list.add(b);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load bills: " + e.getMessage(), e);
        }
        return list;
    }

    public static List<Line> billItems(int billId) {
        List<Line> list = new ArrayList<>();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT charge_type, description, amount FROM bill_item WHERE bill_id = ? ORDER BY item_id")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Line(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load bill items: " + e.getMessage(), e);
        }
        return list;
    }

    public static List<Payment> payments(int billId) {
        List<Payment> list = new ArrayList<>();
        try (Connection c = new connection().connection;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payment_id, bill_id, amount, payment_date, payment_method, reference, remarks " +
                             "FROM finance_payment WHERE bill_id = ? ORDER BY payment_id")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment p = new Payment();
                    p.paymentId = rs.getInt(1);
                    p.billId = rs.getInt(2);
                    p.amount = rs.getBigDecimal(3);
                    Date d = rs.getDate(4);
                    p.date = d == null ? null : d.toLocalDate();
                    p.method = rs.getString(5);
                    p.reference = rs.getString(6);
                    p.remarks = rs.getString(7);
                    list.add(p);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load payments: " + e.getMessage(), e);
        }
        return list;
    }

    /** Builds a printable invoice text for a bill. */
    public static String invoiceText(int billId) {
        Bill target = null;
        for (Bill b : listBills(null, null)) {
            if (b.billId == billId) {
                target = b;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Bill not found.");
        }
        SetupService.Profile profile = SetupService.loadProfile();
        StringBuilder sb = new StringBuilder();
        sb.append("********** ").append(profile.hospitalName.toUpperCase()).append(" **********\n");
        if (!profile.address.isEmpty()) {
            sb.append(profile.address).append(", ").append(profile.city).append("\n");
        }
        if (!profile.phone.isEmpty()) {
            sb.append("Phone: ").append(profile.phone).append("\n");
        }
        sb.append("---------------------------------------------\n");
        sb.append("Bill No    : ").append(target.billNumber).append("\n");
        sb.append("Date       : ").append(target.dateTime).append("\n");
        sb.append("Patient    : ").append(target.patientName).append("\n");
        sb.append("Doctor     : ").append(target.doctorName).append("\n");
        sb.append("Type       : ").append(target.patientType).append("\n");
        sb.append("---------------------------------------------\n");
        for (Line l : billItems(billId)) {
            sb.append(String.format("%-16s %-18s %10s%n", l.chargeType,
                    l.description == null ? "" : l.description, l.amount));
        }
        sb.append("---------------------------------------------\n");
        sb.append(String.format("%-36s %10s%n", "Subtotal", target.subtotal));
        sb.append(String.format("%-36s %10s%n", "Discount", target.discount));
        sb.append(String.format("%-36s %10s%n", "Total Payable", target.total));
        sb.append(String.format("%-36s %10s%n", "Paid", target.paid));
        sb.append(String.format("%-36s %10s%n", "Balance", target.balance));
        sb.append("Status     : ").append(target.status).append("\n");
        sb.append("---------------------------------------------\n");
        sb.append("Thank you for visiting!\n");
        return sb.toString();
    }

    // --------------------------------------------------------------- helpers

    private static BigDecimal paidAmount(Connection c, int billId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(amount),0) FROM finance_payment WHERE bill_id = ?")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private static boolean referenceExists(Connection c, String reference) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM finance_payment WHERE reference = ? LIMIT 1")) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean duplicatePayment(Connection c, int billId, BigDecimal amount, LocalDate date)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM finance_payment WHERE bill_id = ? AND amount = ? AND payment_date = ? LIMIT 1")) {
            ps.setInt(1, billId);
            ps.setBigDecimal(2, amount);
            ps.setDate(3, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void updateStatus(Connection c, int billId, BigDecimal total) throws SQLException {
        BigDecimal paid = paidAmount(c, billId);
        String status;
        if (paid.signum() == 0) {
            status = "Pending";
        } else if (paid.compareTo(total) >= 0) {
            status = "Paid";
        } else {
            status = "Partially Paid";
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE bill SET status = ? WHERE bill_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, billId);
            ps.executeUpdate();
        }
    }

    private static String nextBillNumber(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(bill_number, 5) AS UNSIGNED)), 0) FROM bill " +
                        "WHERE bill_number LIKE 'INV-%'");
             ResultSet rs = ps.executeQuery()) {
            long max = rs.next() ? rs.getLong(1) : 0;
            return String.format("INV-%06d", max + 1);
        }
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
