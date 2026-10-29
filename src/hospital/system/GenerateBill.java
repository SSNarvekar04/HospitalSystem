package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate / manage a patient bill (enhanced).
 *
 * Keeps the original constructor {@code GenerateBill(patient, doctor, type)} so
 * the existing "Book Appointment" flow still works. Adds bill line items
 * (consultation, lab, medicine, bed), subtotal/discount/total calculation, a
 * unique bill number, invoice preview and a first payment. Uses BillingService
 * so validation, status tracking and duplicate-payment rules are centralised.
 */
public class GenerateBill extends JFrame implements ActionListener {

    JTextField patientField, doctorField, amountField, dateTimeField, discountField;
    JComboBox<String> typeBox;
    JComboBox<String> methodBox;
    JTextField payAmountField, payRefField;
    JTextArea receiptArea;
    JButton generateBtn, printBtn, clearBtn, saveBtn, addLineBtn, removeLineBtn, backBtn;

    DefaultTableModel lineModel = new DefaultTableModel(
            new Object[]{"Charge Type", "Description", "Amount"}, 0);
    JTable lineTable = new JTable(lineModel);

    private final String presetLine;

    public GenerateBill() {
        this("", "", "Regular", null);
    }

    public GenerateBill(String patient, String doctor, String type) {
        this(patient, doctor, type, null);
    }

    public GenerateBill(String patient, String doctor, String type, String presetLine) {
        this.presetLine = presetLine;

        setTitle("Generate Bill");
        setSize(760, 760);
        setLayout(null);
        getContentPane().setBackground(new Color(200, 230, 240));

        JLabel heading = new JLabel("Hospital Billing System");
        heading.setFont(new Font("Tahoma", Font.BOLD, 22));
        heading.setBounds(250, 10, 300, 40);
        add(heading);

        JLabel pLabel = new JLabel("Patient Name:");
        pLabel.setBounds(40, 60, 150, 28);
        add(pLabel);
        patientField = new JTextField(patient);
        patientField.setBounds(180, 60, 260, 28);
        add(patientField);

        JLabel dLabel = new JLabel("Doctor Name:");
        dLabel.setBounds(40, 100, 150, 28);
        add(dLabel);
        doctorField = new JTextField(doctor);
        doctorField.setBounds(180, 100, 260, 28);
        add(doctorField);

        JLabel typeLabel = new JLabel("Patient Type:");
        typeLabel.setBounds(40, 140, 150, 28);
        add(typeLabel);
        typeBox = new JComboBox<>(new String[]{"Regular", "New", "OPD", "IPD"});
        typeBox.setSelectedItem(type);
        typeBox.setBounds(180, 140, 260, 28);
        add(typeBox);

        JLabel dtLabel = new JLabel("Date & Time:");
        dtLabel.setBounds(40, 180, 150, 28);
        add(dtLabel);
        dateTimeField = new JTextField();
        dateTimeField.setBounds(180, 180, 260, 28);
        dateTimeField.setEditable(false);
        dateTimeField.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        add(dateTimeField);

        JLabel lineLabel = new JLabel("Add Charge:");
        lineLabel.setBounds(40, 220, 150, 26);
        add(lineLabel);
        addLineBtn = new JButton("Add Charge Line");
        addLineBtn.setBounds(180, 220, 160, 28);
        addLineBtn.setBackground(new Color(70, 160, 90));
        addLineBtn.setForeground(Color.WHITE);
        addLineBtn.addActionListener(this);
        add(addLineBtn);
        removeLineBtn = new JButton("Remove Line");
        removeLineBtn.setBounds(350, 220, 120, 28);
        removeLineBtn.setBackground(new Color(200, 70, 70));
        removeLineBtn.setForeground(Color.WHITE);
        removeLineBtn.addActionListener(this);
        add(removeLineBtn);

        UiUtil.styleTable(lineTable);
        JScrollPane lineSp = new JScrollPane(lineTable);
        lineSp.setBounds(40, 255, 670, 120);
        add(lineSp);

        JLabel discLabel = new JLabel("Discount:");
        discLabel.setBounds(40, 385, 150, 28);
        add(discLabel);
        discountField = new JTextField("0");
        discountField.setBounds(180, 385, 120, 28);
        add(discountField);

        generateBtn = new JButton("Preview Invoice");
        generateBtn.setBounds(40, 425, 150, 32);
        generateBtn.addActionListener(this);
        add(generateBtn);
        printBtn = new JButton("Print");
        printBtn.setBounds(200, 425, 100, 32);
        printBtn.addActionListener(this);
        add(printBtn);
        clearBtn = new JButton("Clear");
        clearBtn.setBounds(310, 425, 100, 32);
        clearBtn.addActionListener(this);
        add(clearBtn);
        saveBtn = new JButton("Save Bill");
        saveBtn.setBounds(420, 425, 130, 32);
        saveBtn.setBackground(new Color(70, 160, 90));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.addActionListener(this);
        add(saveBtn);

        receiptArea = new JTextArea();
        receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane pane = new JScrollPane(receiptArea);
        pane.setBounds(40, 470, 670, 180);
        add(pane);

        backBtn = new JButton("Back");
        backBtn.setBounds(590, 425, 130, 32);
        backBtn.setBackground(new Color(180, 50, 50));
        backBtn.setForeground(Color.WHITE);
        backBtn.addActionListener(this);
        add(backBtn);

        if (presetLine != null) {
            amountField = new JTextField();
            addPresetLine();
        }

        setVisible(true);
        setLocationRelativeTo(null);
    }

    private void addPresetLine() {
        double amt = typeBox.getSelectedItem() != null
                && "New".equalsIgnoreCase(String.valueOf(typeBox.getSelectedItem())) ? 500 : 200;
        lineModel.addRow(new Object[]{"Consultation", presetLine == null ? "Consultation fee" : presetLine, amt});
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == addLineBtn) {
            String desc = JOptionPane.showInputDialog(this, "Charge description:");
            if (desc == null) {
                return;
            }
            String type = (String) JOptionPane.showInputDialog(this, "Charge type:", "Charge Type",
                    JOptionPane.QUESTION_MESSAGE, null,
                    new String[]{"Consultation", "Laboratory", "Medicine", "Bed", "Other"}, "Consultation");
            String amtStr = JOptionPane.showInputDialog(this, "Amount:");
            if (!UiUtil.isPositiveNumber(amtStr)) {
                UiUtil.error("Amount must be a non-negative number.");
                return;
            }
            lineModel.addRow(new Object[]{type == null ? "Other" : type, desc, amtStr});
        }

        else if (e.getSource() == removeLineBtn) {
            int r = lineTable.getSelectedRow();
            if (r != -1) {
                lineModel.removeRow(r);
            }
        }

        else if (e.getSource() == generateBtn) {
            receiptArea.setText(preview());
        }

        else if (e.getSource() == printBtn) {
            if (receiptArea.getText().trim().isEmpty()) {
                receiptArea.setText(preview());
            }
            try {
                receiptArea.print();
            } catch (Exception ex) {
                UiUtil.error("Print failed: " + ex.getMessage());
            }
        }

        else if (e.getSource() == saveBtn) {
            saveBill();
        }

        else if (e.getSource() == clearBtn) {
            patientField.setText("");
            doctorField.setText("");
            lineModel.setRowCount(0);
            discountField.setText("0");
            dateTimeField.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            receiptArea.setText("");
        }

        else if (e.getSource() == backBtn) {
            dispose();
        }
    }

    private List<BillingService.Line> lines() {
        List<BillingService.Line> list = new ArrayList<>();
        for (int i = 0; i < lineModel.getRowCount(); i++) {
            String amtStr = String.valueOf(lineModel.getValueAt(i, 2));
            if (!UiUtil.isPositiveNumber(amtStr)) {
                throw new IllegalArgumentException("Line " + (i + 1) + " has an invalid amount.");
            }
            list.add(new BillingService.Line(
                    String.valueOf(lineModel.getValueAt(i, 0)),
                    String.valueOf(lineModel.getValueAt(i, 1)),
                    new BigDecimal(amtStr.trim())));
        }
        return list;
    }

    private String preview() {
        try {
            List<BillingService.Line> list = lines();
            BigDecimal subtotal = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder();
            sb.append("********** HOSPITAL BILL (PREVIEW) **********\n");
            sb.append("Date & Time : ").append(dateTimeField.getText()).append("\n");
            sb.append("---------------------------------------------\n");
            sb.append("Patient Name : ").append(patientField.getText()).append("\n");
            sb.append("Doctor Name  : ").append(doctorField.getText()).append("\n");
            sb.append("Patient Type : ").append(typeBox.getSelectedItem()).append("\n");
            sb.append("---------------------------------------------\n");
            for (BillingService.Line l : list) {
                sb.append(String.format("%-18s %-24s %10s%n", l.chargeType, l.description, l.amount));
                subtotal = subtotal.add(l.amount);
            }
            BigDecimal disc = parseDiscount();
            if (disc.compareTo(subtotal) > 0) {
                throw new IllegalArgumentException("Discount cannot exceed the subtotal.");
            }
            sb.append("---------------------------------------------\n");
            sb.append(String.format("%-43s %10s%n", "Subtotal", subtotal));
            sb.append(String.format("%-43s %10s%n", "Discount", disc));
            sb.append(String.format("%-43s %10s%n", "Total Payable", subtotal.subtract(disc)));
            sb.append("---------------------------------------------\n");
            return sb.toString();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
            return "";
        }
    }

    private BigDecimal parseDiscount() {
        String v = discountField.getText().trim();
        if (v.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Discount must be a number.");
        }
    }

    private void saveBill() {
        try {
            List<BillingService.Line> list = lines();
            if (list.isEmpty()) {
                UiUtil.error("Add at least one charge line.");
                return;
            }
            int billId = BillingService.createBill(null, patientField.getText(), doctorField.getText(),
                    String.valueOf(typeBox.getSelectedItem()), null, list, parseDiscount());
            UiUtil.info("Bill saved. Bill No: " + billNumber(billId));
            receiptArea.setText(BillingService.invoiceText(billId));
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private String billNumber(int billId) {
        for (BillingService.Bill b : BillingService.listBills(null, null)) {
            if (b.billId == billId) {
                return b.billNumber;
            }
        }
        return String.valueOf(billId);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new GenerateBill();
    }
}
