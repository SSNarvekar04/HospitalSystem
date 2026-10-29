package hospital.system;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable launcher screen used by the three ERP modules.
 * Shows a header with the module name and a grid of action buttons.
 */
public class ModuleDashboard extends JFrame {

    public ModuleDashboard(String title, String[] buttonLabels, Runnable[] actions) {
        setTitle(title);
        setSize(920, 620);
        setLocationRelativeTo(null);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(72, 129, 143), getWidth(), getHeight(), new Color(96, 158, 171));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setLayout(null);
        header.setBounds(0, 0, 920, 120);
        add(header);

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lbl.setForeground(Color.WHITE);
        lbl.setBounds(40, 30, 700, 50);
        header.add(lbl);

        JPanel grid = new JPanel(null);
        grid.setBounds(40, 150, 840, 380);
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(grid);

        font = new Font("Segoe UI", Font.BOLD, 17);
        backgroundColor = new Color(255, 219, 112);

        for (int i = 0; i < buttonLabels.length; i++) {
            int col = i % 3;
            int row = i / 3;
            JButton b = new JButton(buttonLabels[i]);
            b.setBounds(30 + col * 275, 30 + row * 105, 250, 70);
            b.setFont(font);
            b.setBackground(backgroundColor);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createLineBorder(new Color(110, 130, 140), 2));
            final int idx = i;
            b.addActionListener(e -> actions[idx].run());
            grid.add(b);
        }

        JButton back = new JButton("Back");
        back.setBounds(385, 545, 150, 40);
        back.setBackground(new Color(200, 80, 80));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 16));
        back.addActionListener(e -> dispose());
        add(back);

        setVisible(true);
    }

    private Font font;
    private Color backgroundColor;
}