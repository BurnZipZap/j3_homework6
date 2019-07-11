package gb.j2.chat.client;

import gb.j2.chat.library.Messages;
import gb.j2.network.SocketThread;
import gb.j2.network.SocketThreadListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class ClientGUI extends JFrame implements ActionListener, Thread.UncaughtExceptionHandler, SocketThreadListener {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final String TITLE = "Chat Client";

    private final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss: ");

    private final JTextArea log = new JTextArea();
    private final JPanel panelTop = new JPanel(new GridLayout(2, 3));
    private final JTextField tfIPAddress = new JTextField("127.0.0.1");
    private final JTextField tfPort = new JTextField("8189");
    private final JCheckBox cbAlwaysOnTop = new JCheckBox("Alwayson top");
    private final JTextField tfLogin = new JTextField("root");
    private final JPasswordField tfPassword = new JPasswordField("root");
    private final JButton btnLogin = new JButton("Login");

    private final JPanel panelBottom = new JPanel(new BorderLayout());
    private final JButton btnDisconnect = new JButton("<html><b>Disconnect</b></html>");
    private final JTextField tfMessage = new JTextField();
    private final JButton btnSend = new JButton("Send");

    private final JList<String> userList = new JList<>();
    private final String[] EMPTY = new String[0];
    private boolean shownIoErrors = false;
    private SocketThread socketThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ClientGUI();
            }
        });
    }

    private ClientGUI() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setSize(WIDTH, HEIGHT);
        log.setEditable(false);
        log.setLineWrap(true);
        JScrollPane scrollLog = new JScrollPane(log);
        JScrollPane scrollUsers = new JScrollPane(userList);
        scrollUsers.setPreferredSize(new Dimension(100, 0));
        cbAlwaysOnTop.addActionListener(this);
        btnLogin.addActionListener(this);
        btnSend.addActionListener(this);
        tfMessage.addActionListener(this);
        btnDisconnect.addActionListener(this);
        panelTop.add(tfIPAddress);
        panelTop.add(tfPort);
        panelTop.add(cbAlwaysOnTop);
        panelTop.add(tfLogin);
        panelTop.add(tfPassword);
        panelTop.add(btnLogin);
        panelBottom.add(btnDisconnect, BorderLayout.WEST);
        panelBottom.add(tfMessage, BorderLayout.CENTER);
        panelBottom.add(btnSend, BorderLayout.EAST);
        panelBottom.setVisible(false);
        add(panelTop, BorderLayout.NORTH);
        add(scrollLog, BorderLayout.CENTER);
        add(panelBottom, BorderLayout.SOUTH);
        add(scrollUsers, BorderLayout.EAST);
        setVisible(true);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        String msg;
        StackTraceElement[] ste = e.getStackTrace();
        if (ste.length == 0)
            msg = "Empty Stacktrace";
        else {
            msg = e.getClass().getCanonicalName() + ": " + e.getMessage() +
                    "\n\t at " + ste[0];
        }
        JOptionPane.showMessageDialog(null, msg, "Exception", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == cbAlwaysOnTop) {
            setAlwaysOnTop(cbAlwaysOnTop.isSelected());
        } else if (src == btnLogin || src == tfIPAddress || src == tfLogin || src == tfPassword || src == tfPort) {
            connect();
        } else if (src == btnSend || src == tfMessage) {
            sendMessage();
        } else if (src == btnDisconnect) {
            socketThread.close();
        } else {
            throw new RuntimeException("Unknown source: " + src);
        }
    }

    private void connect() {
        Socket socket = null;
        try {
            readMsgToLogFile(50);
            socket = new Socket(tfIPAddress.getText(), Integer.parseInt(tfPort.getText()));
        } catch (IOException e) {
            log.append("Exception: " + e.getMessage());
        }
        socketThread = new SocketThread(this, "Client thread", socket);
    }


    void sendMessage() {
        String msg = tfMessage.getText();
        if ("".equals(msg)) return;
        tfMessage.setText(null);
        tfMessage.requestFocusInWindow();
        //putLog(String.format("%s",msg));
        //System.out.println(Messages.getClientBcast(msg));
        socketThread.sendString(Messages.getClientBcast(msg));
    }

    private void putLog(String msg) {
        if ("".equals(msg)) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.append(msg + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            }
        });
    }

    /**
     * Socket Thread Events
     * */

    @Override
    public void onStartSocketThread(SocketThread thread, Socket socket) {
        putLog("Connection Established");
    }

    @Override
    public void onStopSocketThread(SocketThread thread) {
        putLog("Connection lost");
        panelBottom.setVisible(false);
        panelTop.setVisible(true);
        setTitle(TITLE);
        userList.setListData(EMPTY);
    }

    @Override
    public void onReceiveString(SocketThread thread, Socket socket, String msg) {
        handleMessage(msg);
    }

    @Override
    public void onSocketThreadIsReady(SocketThread thread, Socket socket) {
        panelBottom.setVisible(true);
        panelTop.setVisible(false);
        String login = tfLogin.getText();
        String password = new String(tfPassword.getPassword());
        thread.sendString(Messages.getAuthRequest(login, password));
    }

    @Override
    public void onSocketThreadException(SocketThread thread, Exception e) {
        putLog("socket thread exception");
    }

    private void handleMessage(String value) { //Сообщения!
        String[] arr = value.split(Messages.DELIMITER);
        String msgType = arr[0];
        switch (msgType) {
            case Messages.AUTH_ACCEPT:
                setTitle(TITLE + " logged in as: " + arr[1]);
                break;
            case Messages.AUTH_ERROR:
                putLog(value);
                break;
            case Messages.MESSAGE_FORMAT_ERROR:
                putLog(value);
                socketThread.close();
                break;
            case Messages.BROADCAST:
                String msg = "" + dateFormat.format(Long.parseLong(arr[1])) + arr[2] + ": " + arr[3];
                putLog(msg);
                wrtMsgToLogFile(msg);
                System.out.println("msg: "+msg);
                break;
            case Messages.USER_LIST:
                String users = value.substring(Messages.USER_LIST.length() + Messages.DELIMITER.length());
                String[] userArray = users.split(Messages.DELIMITER);
                Arrays.sort(userArray);
                userList.setListData(userArray);
                break;
            default:
                throw new RuntimeException("Unknown message type: " + value);
        }
    }

    private void wrtMsgToLogFile(String msg) {
        try (FileWriter out = new FileWriter("log.txt", true)) {
            out.write(msg + "\n");
            out.flush();
        } catch (IOException e) {
            if (!shownIoErrors) {
                shownIoErrors = true;
                JOptionPane.showMessageDialog(this, "File write error", "Exception", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void readMsgToLogFile(int countPrintRows) {
        try (Scanner scanner = new Scanner(new File("log.txt"))) {
            ArrayList<String> list = new ArrayList<>();

            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
            }

            int countAllRows = list.size();
            int indexStartRows = (countAllRows>countPrintRows)?countAllRows-countPrintRows-1:0;

            for(int i=indexStartRows; i<countAllRows; i++) {
                putLog(list.get(i));
            }

        } catch (IOException e) {
            if (!shownIoErrors) {
                shownIoErrors = true;
                JOptionPane.showMessageDialog(this, "File read error", "Exception", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


}
