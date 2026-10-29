package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Finance module - record payments against patient bills and view the stream
 * of collected payments. Pending bills are shown separately from collected
 * money (pending = bill.amount - sum of payments for that bill).
 */
public class FinanceBillPayment extends JFrame {

    private JTable billTable;
    private JLabel patientLabel, billedLabel, outstandingLabel;
    private JTextField amountField, dateField, referenceField, remarksField;
    private JComboBox<String> methodBox;

    private JTable paymentTable;
    private JTextField paySearchField, refSearchField;

    public FinanceBillPayment() {
        setTitle("Patient Billing & Payments");
        setSize(1120, 720);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1120, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("PATIENT BILLING & PAYMENTS");
        heading.setBounds(320, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBounds(20, 85, 1080, 540);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(tabs);

        // ---------------- Record payment tab ----------------
        JPanel recPanel = new JPanel(null);
        recPanel.setBackground(Color.WHITE);

        JLabel lBills = new JLabel("Bills (double column shows outstanding amount)");
        lBills.setBounds(20, 12, 400, 24);
        lBills.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lBills.setForeground(new Color(30, 120, 150));
        recPanel.add(lBills);

        billTable = new JTable();
        UiUtil.styleTable(billTable);
        JScrollPane bsp = new JScrollPane(billTable);
        bsp.setBounds(20, 42, 620, 330);
        recPanel.add(bsp);

        billTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && billTable.getSelectedRow() != -1) {
                int r = billTable.getSelectedRow();
                Object o = billTable.getValueAt(r, 6);
                patientLabel.setText(value(r, 1));
                billedLabel.setText("Billed: " + value(r, 4));
                String out = o == null ? "0.00" : o.toString();
                outstandingLabel.setText("Outstanding: " + out);
                amountField.setText(out);
            }
        });

        JPanel form = new JPanel(null);
        form.setBounds(660, 30, 400, 400);
        form.setBackground(new Color(238, 246, 248));
        form.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190)));
        recPanel.add(form);

        int fy = 18;
        patientLabel = new JLabel("Select a bill from the list.");
        patientLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        patientLabel.setBounds(20, fy, 360, 26);
        form.add(patientLabel);

        fy += 36;
        billedLabel = new JLabel("Billed: -");
        billedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        billedLabel.setBounds(20, fy, 360, 26);
        form.add(billedLabel);

        fy += 30;
        outstandingLabel = new JLabel("Outstanding: -");
        outstandingLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        outstandingLabel.setForeground(new Color(170, 80, 30));
        outstandingLabel.setBounds(20, fy, 360, 26);
        form.add(outstandingLabel);

        fy += 40;
        JLabel lAmount = new JLabel("Payment Amount:");
        lAmount.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lAmount.setBounds(20, fy, 130, 26);
        form.add(lAmount);

        amountField = new JTextField();
        amountField.setBounds(150, fy, 160, 26);
        form.add(amountField);

        fy += 36;
        JLabel lDate = new JLabel("Payment Date:");
        lDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lDate.setBounds(20, fy, 130, 26);
        form.add(lDate);

        dateField = new JTextField(LocalDate.now().toString());
        dateField.setBounds(150, fy, 160, 26);
        form.add(dateField);

        fy += 36;
        JLabel lMethod = new JLabel("Method:");
        lMethod.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lMethod.setBounds(20, fy, 130, 26);
        form.add(lMethod);

        methodBox = new JComboBox<>(new String[]{"Cash", "UPI", "Card", "Bank Transfer", "Other"});
        methodBox.setBounds(150, fy, 160, 26);
        form.add(methodBox);

        fy += 36;
        JLabel lRef = new JLabel("Reference:");
        lRef.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lRef.setBounds(20, fy, 130, 26);
        form.add(lRef);

        referenceField = new JTextField();
        referenceField.setBounds(150, fy, 160, 26);
        form.add(referenceField);

        fy += 36;
        JLabel lRem = new JLabel("Remarks:");
        lRem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lRem.setBounds(20, fy, 130, 26);
        form.add(lRem);

        remarksField = new JTextField();
        remarksField.setBounds(150, fy, 160, 26);
        form.add(remarksField);

        fy += 44;
        JButton payBtn = new JButton("Record Payment");
        payBtn.setBounds(120, fy, 180, 34);
        payBtn.setBackground(new Color(70, 160, 90));
        payBtn.setForeground(Color.WHITE);
        payBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        form.add(payBtn);

        tabs.addTab("Record Payment", recPanel);

        // ---------------- Payments received tab ----------------
        JPanel payPanel = new JPanel(null);
        payPanel.setBackground(Color.WHITE);

        JLabel l1 = new JLabel("Patient Name:");
        l1.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l1.setBounds(20, 16, 100, 26);
        payPanel.add(l1);

        paySearchField = new JTextField();
        paySearchField.setBounds(130, 16, 180, 26);
        payPanel.add(paySearchField);

        JLabel l2 = new JLabel("Reference:");
        l2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l2.setBounds(330, 16, 80, 26);
        payPanel.add(l2);

        refSearchField = new JTextField();
        refSearchField.setBounds(415, 16, 180, 26);
        payPanel.add(refSearchField);

        JButton paySearchBtn = new JButton("Search");
        paySearchBtn.setBounds(610, 16, 90, 26);
        paySearchBtn.setBackground(new Color(30, 140, 160));
        paySearchBtn.setForeground(Color.WHITE);
        payPanel.add(paySearchBtn);

        JButton payAllBtn = new JButton("Show All");
        payAllBtn.setBounds(710, 16, 100, 26);
        payAllBtn.setBackground(new Color(180, 130, 60));
        payAllBtn.setForeground(Color.WHITE);
        payPanel.add(payAllBtn);

        paymentTable = new JTable();
        UiUtil.styleTable(paymentTable);
        JScrollPane psp = new JScrollPane(paymentTable);
        psp.setBounds(20, 55, 1040, 440);
        payPanel.add(psp);

        tabs.addTab("Payments Received", payPanel);

        JButton back = new JButton("Back");
        back.setBounds(485, 640, 150, 34);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        payBtn.addActionListener(e -> recordPayment());
        paySearchBtn.addActionListener(e -> loadPayments(true));
        payAllBtn.addActionListener(e -> {
            paySearchField.setText("");
            refSearchField.setText("");
            loadPayments(false);
        });

        loadBills();
        loadPayments(false);
        setVisible(true);
    }

    private String value(int r, int c) {
        Object o = billTable.getValueAt(r, c);
        return o == null ? "" : o.toString();
    }

    private void loadBills() {
        String sql = "SELECT b.bill_id, b.patient_name, b.doctor_name, b.patient_type, b.amount, " +
                "DATE_FORMAT(b.date_time, '%Y-%m-%d %H:%i') AS billed_on, " +
                "(b.amount - IFNULL((SELECT SUM(p.amount) FROM finance_payment p WHERE p.bill_id = b.bill_id), 0)) AS outstanding " +
                "FROM bill b ORDER BY b.bill_id DESC";
        UiUtil.load(billTable, sql);
    }

    private void recordPayment() {
        if (billTable.getSelectedRow() == -1) {
            UiUtil.error("Select a bill from the list first.");
            return;
        }
        String billIdText = value(billTable.getSelectedRow(), 0);
        int billId = Integer.parseInt(billIdText);
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            UiUtil.error("Payment amount must be a valid number.");
            return;
        }
        String date = dateField.getText().trim();
        if (!UiUtil.isValidDate(date)) {
            UiUtil.error("Payment date must be in yyyy-MM-dd format.");
            return;
        }
        try {
            ErpService.recordPayment(billId, amount, LocalDate.parse(date),
                    (String) methodBox.getSelectedItem(),
                    referenceField.getText().trim(),
                    remarksField.getText().trim());
            UiUtil.info("Payment recorded successfully.");
            referenceField.setText("");
            remarksField.setText("");
            loadBills();
            loadPayments(false);
            billTable.clearSelection();
        } catch (IllegalArgumentException ex) {
            UiUtil.error(ex.getMessage());
        } catch (SQLException ex) {
            UiUtil.error(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadPayments(boolean filter) {
        String sql = "SELECT p.payment_id, p.bill_id, p.patient_name, p.amount, " +
                "p.payment_date, p.payment_method, p.reference, p.remarks " +
                "FROM finance_payment p ";
        boolean byName = !paySearchField.getText().trim().isEmpty();
        boolean byRef = !refSearchField.getText().trim().isEmpty();
        if (filter && (byName || byRef)) {
            sql += "WHERE ";
            if (byName) {
                sql += "p.patient_name LIKE ? ";
            }
            if (byName && byRef) {
                sql += "AND ";
            }
            if (byRef) {
                sql += "p.reference LIKE ? ";
            }
        }
        sql += "ORDER BY p.payment_date DESC, p.payment_id DESC LIMIT 500";
        if (filter && byName && byRef) {
            UiUtil.load(paymentTable, sql, "%" + paySearchField.getText().trim() + "%", "%" + refSearchField.getText().trim() + "%");
        } else if (filter && byName) {
            UiUtil.load(paymentTable, sql, "%" + paySearchField.getText().trim() + "%");
        } else if (filter && byRef) {
            UiUtil.load(paymentTable, sql, "%" + refSearchField.getText().trim() + "%");
        } else {
            UiUtil.load(paymentTable, sql);
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new FinanceBillPayment();
    }
}