package gb.j2.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketThread extends Thread {
    private final SocketThreadListener listener;
    private Socket socket;
    private DataOutputStream out;

    public SocketThread(SocketThreadListener listener, String name, Socket socket) {
        super(name);
        this.listener = listener;
        this.socket = socket;
        start();
    }

    @Override
    public void run() {
        try {
            listener.onStartSocketThread(this, socket);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            listener.onSocketThreadIsReady(this, socket);
            while (!isInterrupted()) {
                String msg = in.readUTF();
                listener.onReceiveString(this, socket, msg);
            }
        } catch (IOException e) {
            listener.onSocketThreadException(this, e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                listener.onSocketThreadException(this, e);
            }
            listener.onStopSocketThread(this);
        }

    }

    public boolean sendString(String msg) {
        try {
            out.writeUTF(msg);
            out.flush();
            return true;
        } catch (IOException e) {
            listener.onSocketThreadException(this, e);
            close();
            return false;
        }
    }

    public void close() {
        interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            listener.onSocketThreadException(this, e);
        }
    }
}
