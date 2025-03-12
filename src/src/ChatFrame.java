import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.Base64;


public class ChatFrame extends JFrame {
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JTextPane chatArea;
    private StyledDocument doc;
    private JTextField messageField;
    private JButton sendButton, uploadButton, muteButton;
    private JList<String> usersList;
    private DefaultListModel<String> listModel;
    private boolean muted = false;

    public ChatFrame(String username) {
        this.username = username;
        setTitle("LU-Connect Chat | " + username);
        setSize(600,400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // left panel shows users, center panels shows chat, bottom panel for message input
        JPanel mainPanel = new JPanel(new BorderLayout());

        // left panel, list of all registered users
        listModel = new DefaultListModel<>();
        usersList = new JList<>(listModel);
        refreshUsers(); // if new user signs up while other clients are active
        JScrollPane userScrollPane = new JScrollPane(usersList);
        userScrollPane.setPreferredSize(new Dimension(150,0));
        mainPanel.add(userScrollPane, BorderLayout.WEST); // set to the left

        // center panel, chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        doc = chatArea.getStyledDocument();
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        // bottom panel, message input
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        bottomPanel.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        uploadButton = new JButton("Upload File");
        muteButton = new JButton("Mute");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(uploadButton);
        buttonPanel.add(muteButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(mainPanel);

        // timer that refreshes the list of users every 10 seconds
        Timer timer = new Timer(10000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshUsers();
            }
        });
        timer.start();

        // button listeners
        sendButton.addActionListener(e -> sendMessage()); // listener for sending messages
        messageField.addActionListener(e -> sendMessage());
        uploadButton.addActionListener(e -> uploadFile()); // listener for uploading files
        // listener for mute button
        muteButton.addActionListener(e -> {
            muted = !muted;
            muteButton.setText(muted ? "Unmute" : "Mute"); // change text based on mute state
        });

        // establish server connection, handle waiting if server is full
        try {
            socket = new Socket("localhost", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            initializeConnection();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(ChatFrame.this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

    }

    // handles initial connection and waiting messages
    private void initializeConnection() {
        new Thread(() -> {
            try {
                JDialog waitingDialog = null;
                JLabel waitingLabel = new JLabel();

                // keep reading messages until we get "START" message
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("WAIT:")) {
                        // show/update waiting dialog
                        String waitTime = line.substring(5);
                        if (waitingDialog == null) {
                            waitingDialog = new JDialog(ChatFrame.this, "Waiting", true);
                            waitingDialog.setLayout(new BorderLayout());
                            waitingDialog.add(new JLabel("Maximum number of connect clients reached!"), BorderLayout.NORTH);
                            waitingDialog.add(waitingLabel, BorderLayout.CENTER);
                            waitingDialog.setSize(300, 150);
                            waitingDialog.setLocationRelativeTo(ChatFrame.this);
                            JDialog finalWaitingDialog = waitingDialog;
                            SwingUtilities.invokeLater(() -> finalWaitingDialog.setVisible(true));
                        }
                        SwingUtilities.invokeLater(() -> waitingLabel.setText("Waiting for " + waitTime + " seconds"));
                    } else if (line.equals("START")) {
                        if (waitingDialog != null && waitingDialog.isVisible()) {
                            JDialog finalWaitingDialog1 = waitingDialog;
                            SwingUtilities.invokeLater(() -> finalWaitingDialog1.dispose());
                        }
                        break;
                    }
                }
                out.println("USER:" + username);
                startMessageReader();
            } catch (IOException e){
                e.printStackTrace();
            }
        }).start();
    }

    // background thread responsible for handling incoming messages
    private void startMessageReader() {
        new Thread(() -> {
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    processIncomingMessage(line);
                }
            } catch(IOException e){
                e.printStackTrace();
            }
        }).start();
    }

    // refresh the list of registered users
    private void refreshUsers() {
        String[] users = DatabaseManager.getAllUsers();
        listModel.clear();
        for(String user : users) {
            if (!user.equals(username)) {
                listModel.addElement(user);
            }
        }
    }

    // handles sending messages to other clients
    private void sendMessage() {
        String message = messageField.getText().trim();
        if(message.isEmpty()) return;
        String recipient = usersList.getSelectedValue();
        if(recipient == null) {
            JOptionPane.showMessageDialog(ChatFrame.this, "You must select a user to message");
            return;
        }
        String encryptedMessage = EncryptionUtils.encrypt(message);
        out.println("TO:" + recipient + ":" + encryptedMessage);
        appendChat("To " + recipient + " (" + getCurrentTime() + "): " + message, Color.BLUE);
        messageField.setText("");
    }


    // handles file uploads
    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Allowed files (.docx, .pdf, .jpeg)", "docx", "pdf", "jpeg");
        chooser.setFileFilter(filter); // only allow file types mentioned in the coursework guidelines
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String fileName = file.getName();
            if(!(fileName.endsWith(".docx") || fileName.endsWith(".pdf") || fileName.endsWith(".jpeg"))) {
                JOptionPane.showMessageDialog(this, "File type not allowed.");
                return;
            }
            try {
                // serialize file and encrypt then send
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                String fileData = Base64.getEncoder().encodeToString(fileBytes);
                String encryptedFileData = EncryptionUtils.encrypt(fileData);
                String recipient = usersList.getSelectedValue();
                if(recipient == null) {
                    JOptionPane.showMessageDialog(this,"You must select a user to send a file to");
                    return;
                }
                out.println("FILE:" + recipient + ":" + fileName + ":" + encryptedFileData);
                appendChat("Sent file " + fileName + " to " + recipient + " (" + getCurrentTime() + ")", Color.BLUE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // handle incoming messages
    private void processIncomingMessage(String line) {
        // if text message, display message in chat area and alert user with notification
        if(line.startsWith("FROM:")) {
            String[] parts = line.split(":", 3);
            if(parts.length >= 3) {
                String sender = parts[1];
                String encryptedMessage = parts[2];
                String message = EncryptionUtils.decrypt(encryptedMessage);
                appendChat("From " + sender + " (" + getCurrentTime() + "): " + message, Color.MAGENTA);
                if(!muted) {
                    Toolkit.getDefaultToolkit().beep(); // notification sound
                }
            }
            // if file, deserialize file and save to a folder named downloads
        } else if(line.startsWith("FILEFROM:")) {
            String[] parts = line.split(":", 4);
            if(parts.length >= 4) {
                String sender = parts[1];
                String fileName = parts[2];
                String encryptedFileData = parts[3];
                String fileData = EncryptionUtils.decrypt(encryptedFileData);
                byte[] fileBytes = Base64.getDecoder().decode(fileData);
                try {
                    File downloads = new File("downloads");
                    if(!downloads.exists()) downloads.mkdir();
                    File outFile = new File(downloads, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(fileBytes);
                    }
                    appendChat("Received file " + fileName + " from " + sender + " (" + getCurrentTime() + ")", Color.MAGENTA);
                    if(!muted) {
                        Toolkit.getDefaultToolkit().beep();
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // helper method for appending colored chats to chatPanel
    private void appendChat(String message, Color color) {
        Style style = chatArea.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), message + "\n", style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}
