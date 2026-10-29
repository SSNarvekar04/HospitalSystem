package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Login extends JFrame implements ActionListener {

    JTextField usernameField;
    JPasswordField passwordField;
    JButton loginBtn, cancelBtn;

    Login() {


        setTitle("Hospital Login");
        setSize(750, 320);
        setLocation(400, 270);
        setLayout(null);
        getContentPane().setBackground(new Color(230, 245, 240));

        JLabel nameLabel = new JLabel("Username:");
        nameLabel.setBounds(40, 40, 120, 30);
        nameLabel.setFont(new Font("Tahoma", Font.BOLD, 15));
        add(nameLabel);

        usernameField = new JTextField();
        usernameField.setBounds(160, 40, 180, 30);
        usernameField.setFont(new Font("Tahoma", Font.PLAIN, 15));
        usernameField.setBackground(new Color(240, 240, 240));
        add(usernameField);

        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setBounds(40, 90, 120, 30);
        passwordLabel.setFont(new Font("Tahoma", Font.BOLD, 15));
        add(passwordLabel);

        passwordField = new JPasswordField();
        passwordField.setBounds(160, 90, 180, 30);
        passwordField.setFont(new Font("Tahoma", Font.PLAIN, 15));
        passwordField.setBackground(new Color(240, 240, 240));
        add(passwordField);

        try {
            ImageIcon icon = new ImageIcon(ClassLoader.getSystemResource("icon/login.png"));
            Image img = icon.getImage().getScaledInstance(300, 300, Image.SCALE_SMOOTH);
            JLabel imgLabel = new JLabel(new ImageIcon(img));

            imgLabel.setBounds(380, -20, 350, 300);
            add(imgLabel);
        } catch (Exception e) {
            System.out.println("Login icon not loaded!");
        }

        loginBtn = new JButton("Login");
        loginBtn.setBounds(50, 160, 120, 35);
        loginBtn.setBackground(new Color(50, 150, 90));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFont(new Font("Tahoma", Font.BOLD, 14));
        loginBtn.addActionListener(this);
        add(loginBtn);

        cancelBtn = new JButton("Cancel");
        cancelBtn.setBounds(180, 160, 120, 35);
        cancelBtn.setBackground(new Color(200, 70, 70));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFont(new Font("Tahoma", Font.BOLD, 14));
        cancelBtn.addActionListener(this);
        add(cancelBtn);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == loginBtn) {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText();

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Enter both username and password.");
                return;
            }

            try {
                Rbac.User auth = Rbac.authenticate(user, pass);
                if (auth == null) {
                    auth = Rbac.authenticateLegacy(user, pass);
                }
                if (auth != null) {
                    JOptionPane.showMessageDialog(null, "Login Successful! Welcome, " +
                            (auth.fullName == null || auth.fullName.isEmpty() ? auth.username : auth.fullName)
                            + " (" + auth.role + ")");
                    new Reception();
                    setVisible(false);
                } else {
                    JOptionPane.showMessageDialog(null, "Invalid Username or Password");
                }

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Login error: " + ex.getMessage());
                ex.printStackTrace();
            }

        } else {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        new Login();
    }
}
