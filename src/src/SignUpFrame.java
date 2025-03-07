import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SignUpFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField, confirmPasswordField;
    private JButton signUpButton;

    public SignUpFrame() {
        setTitle("LU-Connect Sign Up");
        setSize(300, 250);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 2));

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        panel.add(new JLabel("Confirm Password:"));
        confirmPasswordField = new JPasswordField();
        panel.add(confirmPasswordField);

        signUpButton = new JButton("Sign Up");
        panel.add(new JLabel());
        panel.add(signUpButton);

        add(panel);

        signUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                String confirmPassword = new String(confirmPasswordField.getPassword());
                if(username.equals("") || password.equals("") || confirmPassword.equals("")) {
                    JOptionPane.showMessageDialog(SignUpFrame.this, "Please fill all the fields correctly.");
                    return;
                }
                if(!password.equals(confirmPassword)) {
                    JOptionPane.showMessageDialog(SignUpFrame.this, "Passwords do not match.");
                    return;
                }
                String hashedPassword = EncryptionUtils.sha256(password);
                if(DatabaseManager.addUser(username, hashedPassword)) {
                    JOptionPane.showMessageDialog(SignUpFrame.this, "User successfully registered.");
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(SignUpFrame.this, "Something went wrong.");
                }
            }
        });
    }

}
