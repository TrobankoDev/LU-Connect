import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class LUConnectServer {
    private static final int PORT = 12345;
    private static final int MAX_CLIENTS = 3; // the maximum number of allowed concurrent clients
    private static Semaphore semaphore = new Semaphore(MAX_CLIENTS);
    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        long startTime = System.currentTimeMillis();
        try {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            // keep waiting while max number of clients is connected
            while (!semaphore.tryAcquire()) {
                long waitTime = (System.currentTimeMillis() - startTime) / 1000;
                out.println("WAIT: " + waitTime);
                Thread.sleep(1000);
            }
            // client has disconnected, notify that they can connect
            out.println("START");
            // hand over to client handler
            ClientHandler handler = new ClientHandler(clientSocket);
            new Thread(handler).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                // first message formatted in form "USER:username"
                String firstLine = in.readLine();
                if (firstLine != null && firstLine.startsWith("USER:")) {
                    // get the username from initial message
                    username = firstLine.substring(5).trim();
                    // place the username and its corresponding clientHandler in hashmap
                    clients.put(username, this);
                    System.out.println("Client " + username + " connected");
                }

                String line;
                while ((line = in.readLine()) != null) {
                    // check either text message or file
                    if (line.startsWith("TO:")) {
                        // if starts with TO, then format is "TO:recipient:encryptedMessage"
                        String[] parts = line.split(":", 3);
                        if (parts.length >= 3) {
                            String recipient = parts[1];
                            String encryptedMessage = parts[2];
                            ClientHandler recipientHandler = clients.get(recipient);
                            if (recipientHandler != null) {
                                recipientHandler.out.println("FROM:" + username + ":" + encryptedMessage);
                            }
                        }
                    } else if (line.startsWith("FILE:")) {
                        // if starts with FILE handle accordingly, format "FILE:recipient:filename:encryptedFileData"
                        String[] parts = line.split(":", 4);
                        if (parts.length >= 4) {
                            String recipient = parts[1];
                            String fileName = parts[2];
                            String encryptedFileData = parts[3];
                            ClientHandler recipientHandler = clients.get(recipient);
                            if (recipientHandler != null) {
                                recipientHandler.out.println("FILEFROM:" + username + ":" + fileName + ":" + encryptedFileData);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                }
                clients.remove(username);
                semaphore.release();
                System.out.println("Client " + username + " disconnected");
            }
        }
    }
}
