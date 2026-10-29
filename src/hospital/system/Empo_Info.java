package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class Empo_Info extends JFrame {

    Empo_Info() {


        setSize(1000, 650);
        setLocation(350, 200);
        setLayout(null);
        setUndecorated(true);


        JPanel header = new JPanel();
        header.setBounds(0, 0, 1000, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("EMPLOYEE INFORMATION");
        heading.setBounds(280, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 28));
        heading.setForeground(Color.WHITE);
        header.add(heading);


        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0,
                        new Color(200, 225, 230),
                        0, getHeight(),
                        new Color(150, 190, 200));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setBounds(0, 70, 1000, 520);
        panel.setLayout(null);
        add(panel);

        JTable table = new JTable();
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(25);
        table.setBackground(new Color(255, 255, 255));

        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 90, 960, 350);
        sp.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 150), 2));
        panel.add(sp);


        JTableHeader th = table.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 14));
        th.setBackground(new Color(30, 140, 160));
        th.setForeground(Color.WHITE);
        th.setPreferredSize(new Dimension(100, 30));


        try {
            connection c = new connection();
            String q = "SELECT * FROM Emp_Info";
            ResultSet rs = c.statement.executeQuery(q);

            DefaultTableModel model = new DefaultTableModel();
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                model.addColumn(rsmd.getColumnName(i));
            }

            while (rs.next()) {
                Object[] rowData = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    rowData[i - 1] = rs.getObject(i);
                }
                model.addRow(rowData);
            }
            table.setModel(model);
        } catch (Exception e) {
            e.printStackTrace();
        }


        JLabel l1 = new JLabel("EMPLOYEE LIST");
        l1.setBounds(20, 50, 400, 30);
        l1.setFont(new Font("Segoe UI", Font.BOLD, 20));
        l1.setForeground(new Color(40, 70, 90));
        panel.add(l1);

        JButton back = new JButton("BACK");
        back.setBounds(430, 455, 140, 35);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 16));
        back.setFocusPainted(false);
        back.setBorder(BorderFactory.createEmptyBorder());

        back.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                back.setBackground(new Color(20, 120, 140));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                back.setBackground(new Color(30, 140, 160));
            }
        });

        back.addActionListener(e -> setVisible(false));
        panel.add(back);

        JPanel footer = new JPanel();
        footer.setBounds(0, 590, 1000, 60);
        footer.setBackground(new Color(30, 140, 160));
        footer.setLayout(null);
        add(footer);

        JLabel link = new JLabel("Website: www.cityhospital.com   |   About Us   |   Contact: +91 9876543210");
        link.setBounds(180, 15, 800, 30);
        link.setForeground(Color.WHITE);
        link.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        footer.add(link);

        setVisible(true);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new Empo_Info();
    }
}
