package gb.j2.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ServerSocketThread extends Thread {
    private final int port;
    private final int timeout;
    private final ServerSocketThreadListener listener;

    public ServerSocketThread(ServerSocketThreadListener listener, String name, int port, int timeout) {
        super(name);
        this.port = port;
        this.timeout = timeout;
        this.listener = listener;
        start();
    }

    @Override
    public void run() {
        listener.onServerThreadStart(this);
        try (ServerSocket server = new ServerSocket(port)) {
            listener.onServerSocketCreated(this, server);
            server.setSoTimeout(timeout);
            Socket socket;
            while (!isInterrupted()) {
                try {
                    socket = server.accept();
                } catch (SocketTimeoutException e) {
                    listener.onAcceptTimeout(this, server);
                    continue;
                }
                listener.onSocketAccepted(this, socket);
            }
        } catch (IOException e) {
            listener.onServerThreadException(this, e);
        } finally {
            listener.onServerThreadStop(this);
        }
    }
}
