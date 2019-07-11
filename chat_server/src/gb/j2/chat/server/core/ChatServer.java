package gb.j2.chat.server.core;

import gb.j2.chat.library.Messages;
import gb.j2.network.ServerSocketThread;
import gb.j2.network.ServerSocketThreadListener;
import gb.j2.network.SocketThread;
import gb.j2.network.SocketThreadListener;

import javax.swing.*;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Vector;

public class ChatServer implements ServerSocketThreadListener, SocketThreadListener {

    private ServerSocketThread server;
    private final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss: ");
    private ChatServerListener listener;
    private Vector<SocketThread> clients = new Vector<>();

    public ChatServer(ChatServerListener listener){
        this.listener = listener;
    }

    public void start(int port) {
        if (server != null && server.isAlive()) {
            putLog("Server is already running");
        } else {
            server = new ServerSocketThread(this, "Chat server", port, 3000);
        }
    }

    public void stop() {
        if (server == null || !server.isAlive()) {
            putLog("Server is not running");
        } else {
            server.interrupt();
        }
    }

    private void putLog(String msg) { //Пишет лог в текстэреа сервера
        msg = dateFormat.format(System.currentTimeMillis()) +
                Thread.currentThread().getName() + ": " + msg;
        listener.onChatServerMessage(msg);
        //System.out.println("LOG: " + msg);
    }

    /**
     * Server Socket Thread Events
     * */

    @Override
    public void onServerThreadStart(ServerSocketThread thread) {
        putLog("server thread start");
        SqlClient.connect();
    }

    @Override
    public void onServerSocketCreated(ServerSocketThread thread, ServerSocket server) {
        putLog("server socket created");
    }

    @Override
    public void onSocketAccepted(ServerSocketThread thread, Socket socket) {
        putLog("socket accepted");
        String name = "SocketThread" + socket.getInetAddress() + ":" + socket.getPort();
        new ClientThread(this, name, socket);
    }

    @Override
    public void onAcceptTimeout(ServerSocketThread thread, ServerSocket server) {
    }

    @Override
    public void onServerThreadException(ServerSocketThread thread, Exception e) {
        putLog("server thread exception");
    }

    @Override
    public void onServerThreadStop(ServerSocketThread thread) {
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).close();
        }
        SqlClient.disconnect();
    }

    /**
     * Socket Thread Events
     * */

    @Override
    public synchronized void onStartSocketThread(SocketThread thread, Socket socket) {
        putLog("start socket thread");
    }

    @Override
    public synchronized void onStopSocketThread(SocketThread thread) { //--------------------------------------
        ClientThread client = (ClientThread) thread;
        clients.remove(thread);
        if (client.isAuthorized() && !client.isReconnect()) {
            sendToAuthorizedClients(Messages.getBroadcast("Server", client.getNickname() + " disconnected"));
            sendToAuthorizedClients(Messages.getUserList(getUsers()));
        }
    }

    @Override
    public synchronized void onReceiveString(SocketThread thread, Socket socket, String msg) {
        ClientThread client = (ClientThread) thread;
        if (client.isAuthorized()) {
            handleAuthMsg(client, msg);
        } else {
            handleNonAuthMsg(client, msg);
        }
    }

    @Override
    public synchronized void onSocketThreadIsReady(SocketThread thread, Socket socket) {
        clients.add(thread);
        putLog("socket thread is ready");
    }

    @Override
    public synchronized void onSocketThreadException(SocketThread thread, Exception e) {
        putLog("Exception" + e.getClass().getName() + ": " + e.getMessage());
    }

    private String getUsers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            sb.append(client.getNickname()).append(Messages.DELIMITER);
        }
        return sb.toString();
    }

    private void handleAuthMsg(ClientThread thread, String msg) { //Рассылка сообщений пользователям
        String[] arr = msg.split(Messages.DELIMITER);
        String msgType = arr[0];
        switch (msgType) {
            case Messages.CLIENT_BCAST:
                sendToAuthorizedClients(Messages.getBroadcast(thread.getNickname(), arr[1]));
                break;
            default:
                thread.msgFormatError("Take yout sh*t back, maafka " + msg);
        }

    }

    private void handleNonAuthMsg(ClientThread newClient, String msg) { //------------------------------------------
        String[] arr = msg.split(Messages.DELIMITER); // "§"
        if (arr.length != 3 || !arr[0].equals(Messages.AUTH_REQUEST)) {
            newClient.msgFormatError(msg);
            return;
        }
        String login = arr[1];
        String password = arr[2];
        String nickname = SqlClient.getNickname(login, password);
        if (nickname == null) {
            putLog(String.format("Invalid login/password: login='%s', password='%s'", login, password));
            newClient.authError();
        } else {
            ClientThread client = findClientThreadByNickname(nickname);
            newClient.authAccept(nickname);
            if (client == null)
                sendToAuthorizedClients(Messages.getBroadcast("Server", nickname + " connected"));
            else {
                client.reconnect();
                clients.remove(client);
            }
        }
        sendToAuthorizedClients(Messages.getUserList(getUsers()));
    }

    private void sendToAuthorizedClients(String msg) {
        String message = isMsgCommandRenameTo(msg)?renameNick(msg):msg;
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            client.sendString(message);
        }
    }

    private synchronized ClientThread findClientThreadByNickname(String nickname) {
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            if (client.getNickname().equals(nickname))
                return client;
        }
        return null;
    }

    /**
     * isMsgCommandRenameTo проверяет, является ли данное сообщение командой на изменение ника (/renameto <newnick>)
     *
     */
    private synchronized boolean isMsgCommandRenameTo(String msg){
        return msg.split(Messages.RENAME+" ").length == 2;
    }


    /**
     * Чтобы изменить имя в чате необходимо написать сообщение "/renameto <новое_имя>".
     * renameNick апдейтит никнейм пользователя, после чего возвращает строку, в которой содержится информация о том,
     * что пользователь сменил ник. Данная информация броадкаститься всем пользователям в чате.
     */
    private synchronized String renameNick(String msg){
        String newName = msg.split(Messages.RENAME+" ")[1];
        String oldName = msg.split(Messages.DELIMITER)[2];
        SqlClient.renameClient(newName,oldName);
        ClientThread client = findClientThreadByNickname(oldName);
        client.close();
        clients.remove(client);
        String serverMassage = msg.split(oldName)[0] + "Server" + Messages.DELIMITER + oldName +
                " RENAME TO " + newName;
        return serverMassage;
    }
}
