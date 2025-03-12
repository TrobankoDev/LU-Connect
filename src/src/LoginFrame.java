import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, signUpButton;

    public LoginFrame() {
        setTitle("LU-Connect Login");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(3, 2));

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        loginButton = new JButton("Login");
        panel.add(loginButton);

        signUpButton = new JButton("Signup");
        panel.add(signUpButton);

        add(panel);

        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                String hashedPassword = EncryptionUtils.sha256(password);
                if(DatabaseManager.verifyUser(username, hashedPassword)) {
                    JOptionPane.showMessageDialog(null, "Logged in successfuly, welcome!");
                    dispose();
                    new ChatFrame(username).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(null, "Invalid credentials.");

                }
            }
        });

        signUpButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                new SignUpFrame().setVisible(true);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}