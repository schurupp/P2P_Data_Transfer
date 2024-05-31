import java.awt.GridLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class P2P extends JFrame {

    private DatagramSocket socket; // Socket used for broadcasting etc.

    private Thread udpBroadcastListenerThread; // Broadcast Listener Thread

    private JTextField folderPathTextField; // Shared folder location
    private JTextField privateKeyPasswordField; // Shared secret

    JFrame ipAndFilesFrame = new JFrame("P2P File Sharing App"); // IP and File frame
    JTextArea ipTextArea = new JTextArea("Computers in Network:"); // IP area

    JTextArea transferInfoTextArea = new JTextArea("File Transfers"); // File transfer area

    // List to hold file names
    DefaultListModel<String> filesListModel = new DefaultListModel<>();
    JList<String> filesList = new JList<>(filesListModel);

    String files = ""; // Total file holder with /// in between

    boolean isMouseListenerAdded = false; // To check if there is already a mouselistener for double click detection
                                          // precaution for multiple download request

    private Map<String, String> connectedNodes = new HashMap<>(); // IP Address and List of Files

    // Main Class Constructor
    public P2P() {

        setTitle("P2P File Sharing App");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        createUI();

        setLocationRelativeTo(null);
        setVisible(true);

        startUDPBroadcastListener();
    }

    // Create necessary UI elements
    private void createUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 2, 10, 10));

        JLabel folderPathLabel = new JLabel("Shared Folder Location:");
        folderPathTextField = new JTextField();
        JLabel privateKeyLabel = new JLabel("Shared Secret:");
        privateKeyPasswordField = new JTextField();

        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connectAction());

        addMenu();
        panel.add(folderPathLabel);
        panel.add(folderPathTextField);
        panel.add(privateKeyLabel);
        panel.add(privateKeyPasswordField);
        panel.add(new JLabel());
        panel.add(connectButton);

        add(panel);
    }

    // Create necessary Menu elements
    private void addMenu() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem connectMenuItem = new JMenuItem("Connect");
        connectMenuItem.addActionListener(e -> connectAction());

        JMenuItem disconnectMenuItem = new JMenuItem("Disconnect");
        disconnectMenuItem.addActionListener(e -> disconnectAction());
        disconnectMenuItem.setEnabled(false);

        fileMenu.add(connectMenuItem);
        fileMenu.add(disconnectMenuItem);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        JMenuItem aboutMeMenuItem = new JMenuItem("About Me");
        aboutMeMenuItem.addActionListener(e -> showAboutMe());

        helpMenu.add(aboutMeMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    // Handling the logic of connection
    private void connectAction() {
        String folderPath = folderPathTextField.getText();
        String privateKey = privateKeyPasswordField.getText();

        listFiles(folderPath); // Get the files in file location

        // Start the listener if not already created
        if (udpBroadcastListenerThread == null || !udpBroadcastListenerThread.isAlive()) {
            startUDPBroadcastListener();
        }

        // Debugging purposes
        System.out.println("Connecting to Shared Folder: " + folderPath);
        System.out.println("Private Key: " + new String(privateKey));

        initiateUDPBroadcast("NodeInfo"); // Broadcast your information
        displayIPAndFilesFrame(); // Open the IP and File frame
        setDisconnectMenuItemEnabled(true); // Enable disconnect menu button
    }

    // Handling the logic of disconnection
    private void disconnectAction() {
        connectedNodes.clear(); // Clear the hashmap of ip and file
        updateGUI(ipTextArea, filesList); // Update the GUI so it's fresh
        initiateUDPBroadcast("Disconnect"); // Broadcast Disconnect for other nodes
        setDisconnectMenuItemEnabled(false); // Disable disconnect menu button
        // Close the socket if not already closed
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        files = ""; // Clear file string
        // Dispose the frame if not already disposed
        if (ipAndFilesFrame != null && ipAndFilesFrame.isVisible()) {
            ipAndFilesFrame.dispose();
        }
    }

    // Handling the menu connect and disconnect activition
    private void setDisconnectMenuItemEnabled(boolean enabled) {
        JMenuBar menuBar = getJMenuBar();
        if (menuBar != null) {
            JMenu fileMenu = menuBar.getMenu(0);
            if (fileMenu != null) {
                JMenuItem disconnectMenuItem = fileMenu.getItem(1);
                if (disconnectMenuItem != null) {
                    disconnectMenuItem.setEnabled(enabled);
                }
            }
        }
    }

    // About me section
    private void showAboutMe() {
        JOptionPane.showMessageDialog(this,
                "Fatih Yeşilyayla 20200702004\nJust enter location and key and double click the file you wanna get:)");
    }

    // IP and file frame
    private void displayIPAndFilesFrame() {
        if (ipAndFilesFrame != null) {
            ipAndFilesFrame.dispose();
        }
        ipAndFilesFrame = new JFrame("P2P File Sharing App"); // Name

        // Some UI settings and initializations
        ipTextArea.setEditable(false);
        transferInfoTextArea.setEditable(false);
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));
        JPanel topPanel = new JPanel(new GridLayout(1, 2));

        // Handling the double click for files
        filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!isMouseListenerAdded) {
            filesList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int selectedIndex = filesList.getSelectedIndex();
                        if (selectedIndex != -1) {
                            downloadAction();
                        }
                    }
                }
            });
            isMouseListenerAdded = true;
        }

        // Some UI settings and initializations
        JScrollPane ipScrollPane = new JScrollPane(ipTextArea);
        JScrollPane filesScrollPane = new JScrollPane(filesList);
        topPanel.add(ipScrollPane);
        topPanel.add(filesScrollPane);
        JScrollPane transferInfoScrollPane = new JScrollPane(transferInfoTextArea);
        panel.add(topPanel);
        panel.add(transferInfoScrollPane);
        ipAndFilesFrame.add(panel);
        ipAndFilesFrame.setSize(600, 400);
        ipAndFilesFrame.setLocationRelativeTo(null);
        ipAndFilesFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        ipAndFilesFrame.setVisible(true);

        // Adding listener for the X button of the frame so it disconnects properly
        ipAndFilesFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectAction();
            }
        });
    }

    // UI updater so when some broadcast about update or disconnection captured it
    // updates the UI accordingly
    private void updateGUI(JTextArea ipTextArea, JList<String> filesList) {
        ipTextArea.setText("");
        DefaultListModel<String> filesListModel = new DefaultListModel<>();
        connectedNodes.forEach((ip, fileList) -> {
            String[] piecesOfFiles = fileList.split("///");
            for (String piece : piecesOfFiles) {
                filesListModel.addElement(piece);
            }
            ipTextArea.append("-" + ip + "\n");
        });
        filesList.setModel(filesListModel);
    }

    // Getting the files for the path entered in the starting
    private void listFiles(String folderPath) {
        try {
            Files.walk(Paths.get(folderPath))
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String filePath = file.toString();
                        files += filePath + "///";
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getting the Overlay IP address of the self node
    private String getOverlayIPAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.getName().equals("enp0s3")) {
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();

                        if (inetAddress instanceof Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Initiating broadcast for 3 message types
    private void initiateUDPBroadcast(String messageType) {
        try {
            InetAddress broadcastAddress = InetAddress.getByName("10.0.2.255");
            int broadcastPort = 9876;
            String enteredKey = privateKeyPasswordField.getText();
            String message;

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                if ("NodeInfo".equals(messageType)) {
                    message = "NodeInfo:" + getOverlayIPAddress() + ","
                            + files + "," + enteredKey;
                } else if ("NodeUpdate".equals(messageType)) {
                    message = "NodeUpdate:" + getOverlayIPAddress() + ","
                            + files + "," + enteredKey;
                } else if ("Disconnect".equals(messageType)) {
                    message = "Disconnect:" + getOverlayIPAddress() + "," + files;
                } else {
                    return;
                }

                byte[] sendData = message.getBytes();

                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, broadcastAddress, broadcastPort);

                socket.send(packet);
            }
        } catch (

        Exception e) {
            e.printStackTrace();
        }
    }

    // Broadcast listener
    private void startUDPBroadcastListener() {
        udpBroadcastListenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(9876);
                byte[] receiveData = new byte[1024];

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    processUDPBroadcast(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        udpBroadcastListenerThread.start();
    }

    // Method where broadcasts are processed depending on the message head
    private void processUDPBroadcast(String message) {
        String[] parts = message.split(":");
        String messageType = parts[0];

        System.out.println("Received message type: " + messageType + " from " + parts[1].split(",")[0]);

        if ("NodeInfo".equals(messageType)) {
            String[] infoParts = parts[1].split(",");
            String ip = infoParts[0];
            String receivedKey = infoParts[2];
            String enteredKey = privateKeyPasswordField.getText();

            if (enteredKey.equals(receivedKey)) {
                String files = infoParts[1];
                connectedNodes.put(ip, files);
                updateGUI(ipTextArea, filesList);
                initiateUDPBroadcast("NodeUpdate");
            } else {
                System.out.println("Private keys don't match. Ignoring connection.");
            }
        } else if ("NodeUpdate".equals(messageType)) {
            String[] infoParts = parts[1].split(",");
            String ip = infoParts[0];
            String receivedKey = infoParts[2];
            String enteredKey = privateKeyPasswordField.getText();

            if (enteredKey.equals(receivedKey) && !connectedNodes.containsKey(ip)) {
                String files = infoParts[1];
                connectedNodes.put(ip, files);
                updateGUI(ipTextArea, filesList);
            }
        } else if ("Disconnect".equals(messageType)) {
            String[] infoParts = parts[1].split(",");
            String ip = infoParts[0];
            connectedNodes.remove(ip);
            updateGUI(ipTextArea, filesList);
        } else if ("DownloadRequest".equals(messageType)) {
            String[] infoParts = parts[1].split(",");
            String fileName = infoParts[0];
            String requesterIP = infoParts[1];
            System.out.println("Received Download Request : " + fileName + " " + requesterIP);
            sendFile(fileName, requesterIP);
        } else if ("FileTransfer".equals(messageType)) {
            String[] infoParts = parts[1].split(",");
            String fileName = infoParts[0];
            String fileLength = infoParts[1];
            String ownerIP = infoParts[2];
            System.out.println("Received File Transfer : " + fileName + " " + fileLength);
            receiveFile(fileName, fileLength, ownerIP);
        } else {
            System.out.println("Received invalid message type.");
        }
    }

    // Method for sending DownloadRequest message
    private void downloadAction() {

        String selectedFile = filesList.getSelectedValue();
        String selectedNodeIP = getSelectedNodeIP(selectedFile);

        if (!selectedNodeIP.equals(getOverlayIPAddress())) {
            try {
                InetAddress destIP = InetAddress.getByName(selectedNodeIP);
                try (DatagramSocket socket = new DatagramSocket()) {
                    String message = "DownloadRequest:" + selectedFile + "," + getOverlayIPAddress();
                    byte[] sendData = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, destIP, 9876);
                    socket.send(packet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Same IPs local file!");
        }

    }

    // Method for handling file sending
    private void sendFile(String filePath, String receiverIP) {
        try {
            File fileToSend = new File(filePath);
            byte[] fileData = Files.readAllBytes(fileToSend.toPath());

            InetAddress destIP = InetAddress.getByName(receiverIP);
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = "FileTransfer:" + fileToSend.getName() + "," + fileData.length + ","
                        + getOverlayIPAddress();
                byte[] sendData = message.getBytes();
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, destIP, 9876);
                socket.send(packet);

                Thread.sleep(100);

                int chunkSize = 32000; // 32kB
                int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);

                for (int i = 0; i < totalChunks; i++) {
                    int offset = i * chunkSize;
                    int length = Math.min(chunkSize, fileData.length - offset);
                    byte[] chunk = Arrays.copyOfRange(fileData, offset, offset + length);
                    DatagramPacket dataPacket = new DatagramPacket(chunk, length, destIP, 9876);
                    socket.send(dataPacket);
                }
                System.out.println("Waited and sent the file");
                String transferInfo = String.format("%nFILE SENT: %s to %s in %s chunks of %s byte sized!",
                        fileToSend.getName(), receiverIP, totalChunks, chunkSize);
                transferInfoTextArea.append(transferInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method for file receiving
    private void receiveFile(String fileName, String fileLength, String senderIP) {
        try {
            int fileSize = Integer.parseInt(fileLength);
            String folderPath = folderPathTextField.getText();

            if (folderPath != null && fileName != null) {
                File outputFile = new File(folderPath, fileName);

                try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                    int chunkSize = 32000; // 32 kB
                    int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

                    for (int i = 0; i < totalChunks; i++) {
                        byte[] receivedData = new byte[chunkSize];
                        DatagramPacket receivePacket = new DatagramPacket(receivedData, receivedData.length);
                        socket.receive(receivePacket);

                        int bytesRead = receivePacket.getLength();
                        if (bytesRead <= 0) {
                            break;
                        }

                        byte[] chunk = Arrays.copyOf(receivedData, bytesRead);
                        fileOutputStream.write(chunk);
                    }

                    System.out.println("File received and saved at: " + outputFile.getAbsolutePath());

                    String transferInfo = String.format("%nFILE RECEIVED: %s from %s in %s chunks of %s byte sized!",
                            fileName,
                            senderIP, totalChunks, chunkSize);
                    transferInfoTextArea.append(transferInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Invalid folderPath or fileName.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method for getting the IP Address of the file owner
    private String getSelectedNodeIP(String selectedFile) {
        for (Map.Entry<String, String> entry : connectedNodes.entrySet()) {
            String ipAddress = entry.getKey();
            String fileList = entry.getValue();
            String[] piecesOfFiles = fileList.split("///");
            if (piecesOfFiles.length > 0) {
                for (String filePath : piecesOfFiles) {
                    if (filePath.endsWith(selectedFile)) {
                        return ipAddress;
                    }
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new P2P();
            }
        });
    }
}
