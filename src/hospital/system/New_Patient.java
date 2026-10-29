package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * New patient registration (enhanced).
 *
 * Keeps the original look and the classic required fields, and adds the
 * optional contact/address/emergency/blood-group/OPD-IPD fields. Uses
 * PatientService so a unique patient code is generated and obvious duplicates
 * are rejected.
 */
public class New_Patient extends JFrame implements ActionListener {
    JComboBox<String> comboBox;
    JTextField textFieldNumber, textName, textFieldDisease;
    JTextField textDob, textAge, textContact, textAddress, textEmergency, textBlood;
    JComboBox<String> typeBox;
    JRadioButton r1, r2;
    JButton b1, b2;
    JLabel date;

    New_Patient() {
        JPanel panel = new JPanel();
        panel.setBounds(5, 5, 940, 600);
        panel.setBackground(new Color(90, 156, 163));
        panel.setLayout(null);
        add(panel);

        JLabel labelTitle = new JLabel("New Patient");
        labelTitle.setBounds(118, 11, 260, 53);
        labelTitle.setFont(new Font("Tahoma", Font.BOLD, 20));
        labelTitle.setForeground(Color.WHITE);
        panel.add(labelTitle);

        JLabel labelID = new JLabel("ID Type:");
        labelID.setBounds(35, 66, 200, 14);
        style(labelID);
        panel.add(labelID);

        comboBox = new JComboBox<>(new String[]{"Aadhar Card", "Voter Id", "Driving License"});
        comboBox.setBounds(271, 63, 150, 20);
        comboBox.setBackground(new Color(3, 45, 48));
        comboBox.setForeground(Color.white);
        comboBox.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(comboBox);

        JLabel labelNumber = new JLabel("Number:");
        labelNumber.setBounds(35, 101, 200, 14);
        style(labelNumber);
        panel.add(labelNumber);

        textFieldNumber = new JTextField();
        textFieldNumber.setBounds(271, 101, 150, 20);
        panel.add(textFieldNumber);

        JLabel labelName = new JLabel("Name:");
        labelName.setBounds(35, 141, 200, 14);
        style(labelName);
        panel.add(labelName);

        textName = new JTextField();
        textName.setBounds(271, 141, 150, 20);
        panel.add(textName);

        JLabel labelGender = new JLabel("Gender:");
        labelGender.setBounds(35, 181, 200, 14);
        style(labelGender);
        panel.add(labelGender);

        r1 = new JRadioButton("Male");
        styleRadio(r1);
        r1.setBounds(271, 181, 80, 15);
        panel.add(r1);

        r2 = new JRadioButton("Female");
        styleRadio(r2);
        r2.setBounds(350, 181, 80, 15);
        panel.add(r2);

        ButtonGroup bg = new ButtonGroup();
        bg.add(r1);
        bg.add(r2);
        r1.setSelected(true);

        JLabel labelDisease = new JLabel("Disease:");
        labelDisease.setBounds(35, 221, 200, 14);
        style(labelDisease);
        panel.add(labelDisease);

        textFieldDisease = new JTextField();
        textFieldDisease.setBounds(271, 221, 150, 20);
        panel.add(textFieldDisease);

        JLabel labelDob = new JLabel("DOB (yyyy-MM-dd):");
        labelDob.setBounds(35, 261, 200, 14);
        style(labelDob);
        panel.add(labelDob);
        textDob = new JTextField();
        textDob.setBounds(271, 261, 150, 20);
        panel.add(textDob);

        JLabel labelAge = new JLabel("Age:");
        labelAge.setBounds(35, 301, 200, 14);
        style(labelAge);
        panel.add(labelAge);
        textAge = new JTextField();
        textAge.setBounds(271, 301, 150, 20);
        panel.add(textAge);

        JLabel labelContact = new JLabel("Contact:");
        labelContact.setBounds(470, 66, 200, 14);
        style(labelContact);
        panel.add(labelContact);
        textContact = new JTextField();
        textContact.setBounds(650, 63, 180, 20);
        panel.add(textContact);

        JLabel labelAddress = new JLabel("Address:");
        labelAddress.setBounds(470, 101, 200, 14);
        style(labelAddress);
        panel.add(labelAddress);
        textAddress = new JTextField();
        textAddress.setBounds(650, 101, 180, 20);
        panel.add(textAddress);

        JLabel labelEmergency = new JLabel("Emergency Contact:");
        labelEmergency.setBounds(470, 141, 200, 14);
        style(labelEmergency);
        panel.add(labelEmergency);
        textEmergency = new JTextField();
        textEmergency.setBounds(650, 141, 180, 20);
        panel.add(textEmergency);

        JLabel labelBlood = new JLabel("Blood Group:");
        labelBlood.setBounds(470, 181, 200, 14);
        style(labelBlood);
        panel.add(labelBlood);
        textBlood = new JTextField();
        textBlood.setBounds(650, 181, 180, 20);
        panel.add(textBlood);

        JLabel labelType = new JLabel("Patient Type:");
        labelType.setBounds(470, 221, 200, 14);
        style(labelType);
        panel.add(labelType);
        typeBox = new JComboBox<>(new String[]{"OPD", "IPD"});
        typeBox.setBounds(650, 221, 180, 20);
        panel.add(typeBox);

        JLabel labelDate = new JLabel("Time:");
        labelDate.setBounds(35, 341, 200, 14);
        style(labelDate);
        panel.add(labelDate);

        date = new JLabel("" + new java.util.Date());
        date.setBounds(271, 341, 300, 14);
        date.setForeground(Color.white);
        date.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(date);

        b1 = new JButton("Add");
        b1.setBounds(140, 430, 120, 30);
        b1.setForeground(Color.white);
        b1.setBackground(new Color(109, 164, 170));
        b1.addActionListener(this);
        panel.add(b1);

        b2 = new JButton("Back");
        b2.setBounds(300, 430, 120, 30);
        b2.setForeground(Color.white);
        b2.setBackground(new Color(200, 100, 100));
        b2.addActionListener(this);
        panel.add(b2);

        setUndecorated(true);
        setSize(950, 610);
        setLayout(null);
        setLocation(300, 200);
        setVisible(true);
    }

    private void style(JLabel l) {
        l.setFont(new Font("Tahoma", Font.BOLD, 14));
        l.setForeground(Color.white);
    }

    private void styleRadio(JRadioButton r) {
        r.setFont(new Font("Tahoma", Font.BOLD, 14));
        r.setForeground(Color.white);
        r.setBackground(new Color(90, 156, 163));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == b1) {
            String radioBT = r1.isSelected() ? "Male" : r2.isSelected() ? "Female" : "";
            if (radioBT.isEmpty()) {
                UiUtil.error("Please select a gender.");
                return;
            }
            if (textDob.getText().trim().isEmpty() && textAge.getText().trim().isEmpty()) {
                UiUtil.error("Enter a date of birth or an age.");
                return;
            }
            try {
                PatientService.Patient p = new PatientService.Patient();
                p.idType = (String) comboBox.getSelectedItem();
                p.number = textFieldNumber.getText();
                p.name = textName.getText();
                p.gender = radioBT;
                p.disease = textFieldDisease.getText();
                p.dateOfBirth = textDob.getText();
                p.age = textAge.getText();
                p.contact = textContact.getText();
                p.address = textAddress.getText();
                p.emergencyContact = textEmergency.getText();
                p.bloodGroup = textBlood.getText();
                p.patientType = (String) typeBox.getSelectedItem();
                int id = PatientService.register(p);
                PatientService.Patient saved = PatientService.findById(id);
                UiUtil.info("Added Successfully. Patient Code: "
                        + (saved == null ? "" : saved.patientCode));
                setVisible(false);
            } catch (RuntimeException ex) {
                UiUtil.error(ex.getMessage());
            }
        } else {
            setVisible(false);
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new New_Patient();
    }
}
