package hospital.system;

import javax.swing.*;
import java.awt.*;

public class LogoLoader {

    public static JLabel getLogo(int width, int height) {

        ImageIcon icon = new ImageIcon(ClassLoader.getSystemResource("icon/logo1.png"));
        Image image = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        ImageIcon scaledIcon = new ImageIcon(image);

        JLabel label = new JLabel(scaledIcon);
        return label;
    }
}
