package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;


public class Department extends JFrame {
    Department (){
        JPanel panel = new JPanel();
        panel.setBounds(5,5,690,490);
        panel.setLayout(null);
        panel.setBackground(new Color(190,164,170));
        add(panel);

        JTable table=new JTable();
        table.setBounds(0,40,700,350);
        table.setFont(new Font("Tahoma",Font.BOLD,14));
        panel.setBackground(new Color(190,164,170));
        panel.add(table);

        try {
            connection c = new connection();
            String q = "SELECT * FROM department";
            ResultSet rs = c.statement.executeQuery(q);

            DefaultTableModel model = new DefaultTableModel();

            ResultSetMetaData ResultSetMetaData = rs.getMetaData();
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

        JLabel label1 = new JLabel("Department");
        label1.setBounds(10,11,105,20);
        label1.setFont(new Font("Tahoma",Font.BOLD,14));
        panel.add(label1);

        JLabel label2 = new JLabel("Phone_No");
        label2.setBounds(351,11,150,20);
        label2.setFont(new Font("Tahoma",Font.BOLD,14));
        panel.add(label2);


        JButton b1 = new JButton("BACK");
        b1.setBounds(400,410,130,30);
        b1.setBackground(Color.BLACK);
        b1.setForeground(Color.WHITE);
        panel.add(b1);
        b1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });



        setUndecorated(true);
        setSize(700,500);
        setLayout(null);
        setLocation(350,250);
        setVisible(true);

    }
    public static void main (String[] args) {

        new Department();
    }
}

