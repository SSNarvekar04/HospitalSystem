package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Billing & payments register.
 *
 * Lists bills with subtotal/discount/total/paid/balance/status, shows bill lines
 * and payment history, records payments (method + reference, validation handled
 * by BillingService), issues refunds and prints an invoice.
 */
public class ManageBills extends JFrame {

    private final JTable billTable = new JTable();
    private final JTable lineTable = new JTable();
    private final JTable paymentTable = new JTable();
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> statusFilter =
            new JComboBox<>(new String[]{"All", "Pending", "Partially Paid", "Paid", "Refunded"});
    private final JTextField payAmount = new JTextField();
    private final JTextField payRef = new JTextField();
    private final JComboBox<String> payMethod =
            new JComboBox<>(new String[]{"Cash", "UPI", "Card", "Net Banking", "Cheque"});
    private final JTextField payDate = new JTextField(LocalDate.now().toString());
    private BillingService.Bill selected;

    public ManageBills() {
        setTitle("Billing & Payments");
        setSize(1160, 720);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1160, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);
        JLabel heading = new JLabel("BILLING & PAYMENTS");
        heading.setBounds(430, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(20, 85, 1120, 590);
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(body);

        searchField.setBounds(20, 15, 220, 28);
        body.add(searchField);
        JButton search = new JButton("Search");
        search.setBounds(245, 15, 90, 28);
        search.addActionListener(e -> loadBills());
        body.add(search);
        statusFilter.setBounds(345, 15, 150, 28);
        statusFilter.addActionListener(e -> loadBills());
        body.add(statusFilter);
        JButton refresh = new JButton("Refresh");
        refresh.setBounds(505, 15, 90, 28);
        refresh.addActionListener(e -> loadBills());
        body.add(refresh);

        UiUtil.styleTable(billTable);
        JScrollPane sp = new JScrollPane(billTable);
        sp.setBounds(20, 55, 1080, 260);
        body.add(sp);

        billTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && billTable.getSelectedRow() != -1) {
                Object id = billTable.getValueAt(billTable.getSelectedRow(), 0);
                selectBill(id == null ? -1 : Integer.parseInt(id.toString()));
            }
        });

        JLabel l1 = new JLabel("Bill Items");
        l1.setBounds(20, 325, 200, 22);
        l1.setFont(new Font("Segoe UI", Font.BOLD, 14));
        body.add(l1);
        UiUtil.styleTable(lineTable);
        JScrollPane sp1 = new JScrollPane(lineTable);
        sp1.setBounds(20, 350, 530, 225);
        body.add(sp1);

        JLabel l2 = new JLabel("Payment History");
        l2.setBounds(570, 325, 200, 22);
        l2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        body.add(l2);
        UiUtil.styleTable(paymentTable);
        JScrollPane sp2 = new JScrollPane(paymentTable);
        sp2.setBounds(570, 350, 530, 225);
        body.add(sp2);

        JLabel l3 = new JLabel("Record Payment");
        l3.setBounds(20, 585, 200, 22);
        l3.setFont(new Font("Segoe UI", Font.BOLD, 14));
        body.add(l3);
        payAmount.setBounds(150, 585, 100, 26);
        body.add(payAmount);
        payMethod.setBounds(255, 585, 120, 26);
        body.add(payMethod);
        payRef.setBounds(380, 585, 130, 26);
        body.add(payRef);
        payDate.setBounds(515, 585, 110, 26);
        body.add(payDate);
        JButton pay = new JButton("Add Payment");
        pay.setBounds(630, 585, 120, 26);
        pay.setBackground(new Color(70, 160, 90));
        pay.setForeground(Color.WHITE);
        pay.addActionListener(e -> recordPayment());
        body.add(pay);

        JButton refund = new JButton("Refund");
        refund.setBounds(755, 585, 100, 26);
        refund.setBackground(new Color(200, 70, 70));
        refund.setForeground(Color.WHITE);
        refund.addActionListener(e -> refund());
        body.add(refund);

        JButton print = new JButton("Print Invoice");
        print.setBounds(860, 585, 140, 26);
        print.setBackground(new Color(30, 140, 160));
        print.setForeground(Color.WHITE);
        print.addActionListener(e -> printInvoice());
        body.add(print);

        JButton back = new JButton("Back");
        back.setBounds(505, 685, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        loadBills();
        setVisible(true);
    }

    private void loadBills() {
        try {
            List<BillingService.Bill> bills = BillingService.listBills(
                    searchField.getText(), (String) statusFilter.getSelectedItem());
            DefaultTableModel m = new DefaultTableModel(
                    new Object[]{"Bill ID", "Bill No", "Patient", "Doctor", "Subtotal", "Discount",
                            "Total", "Paid", "Balance", "Status", "Date"}, 0);
            for (BillingService.Bill b : bills) {
                m.addRow(new Object[]{b.billId, b.billNumber, b.patientName, b.doctorName, b.subtotal,
                        b.discount, b.total, b.paid, b.balance, b.status, b.dateTime});
            }
            billTable.setModel(m);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void selectBill(int billId) {
        if (billId < 0) {
            return;
        }
        for (BillingService.Bill b : BillingService.listBills(null, null)) {
            if (b.billId == billId) {
                selected = b;
                break;
            }
        }
        try {
            DefaultTableModel lineM = new DefaultTableModel(
                    new Object[]{"Charge Type", "Description", "Amount"}, 0);
            for (BillingService.Line l : BillingService.billItems(billId)) {
                lineM.addRow(new Object[]{l.chargeType, l.description, l.amount});
            }
            lineTable.setModel(lineM);

            DefaultTableModel payM = new DefaultTableModel(
                    new Object[]{"ID", "Amount", "Date", "Method", "Reference"}, 0);
            for (BillingService.Payment p : BillingService.payments(billId)) {
                payM.addRow(new Object[]{p.paymentId, p.amount, p.date, p.method, p.reference});
            }
            paymentTable.setModel(payM);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void recordPayment() {
        if (selected == null) {
            UiUtil.error("Select a bill first.");
            return;
        }
        if (!UiUtil.isStrictlyPositiveNumber(payAmount.getText())) {
            UiUtil.error("Enter a valid payment amount.");
            return;
        }
        if (!UiUtil.isValidDate(payDate.getText())) {
            UiUtil.error("Payment date must be yyyy-MM-dd.");
            return;
        }
        try {
            BillingService.recordPayment(selected.billId, new BigDecimal(payAmount.getText().trim()),
                    LocalDate.parse(payDate.getText().trim()), (String) payMethod.getSelectedItem(),
                    payRef.getText(), "");
            UiUtil.info("Payment recorded.");
            payAmount.setText("");
            payRef.setText("");
            loadBills();
            selectBill(selected.billId);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void refund() {
        if (selected == null) {
            UiUtil.error("Select a bill first.");
            return;
        }
        if (!UiUtil.confirm("Mark this bill as Refunded?")) {
            return;
        }
        try {
            BillingService.refund(selected.billId, "Manual refund");
            UiUtil.info("Bill marked as Refunded.");
            loadBills();
            selectBill(selected.billId);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void printInvoice() {
        if (selected == null) {
            UiUtil.error("Select a bill first.");
            return;
        }
        try {
            JTextArea area = new JTextArea(BillingService.invoiceText(selected.billId));
            area.setFont(new Font("Monospaced", Font.PLAIN, 13));
            area.setEditable(false);
            JScrollPane sp = new JScrollPane(area);
            sp.setPreferredSize(new Dimension(560, 420));
            int opt = JOptionPane.showOptionDialog(this, sp, "Invoice",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[]{"Print", "Close"}, "Close");
            if (opt == 0) {
                try {
                    area.print();
                } catch (java.awt.print.PrinterException pe) {
                    UiUtil.error("Print failed: " + pe.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageBills();
    }
}
