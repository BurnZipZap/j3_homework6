package gb.j2.network;

import java.net.ServerSocket;
import java.net.Socket;

public interface ServerSocketThreadListener {

    void onServerThreadStart(ServerSocketThread thread);
    void onServerSocketCreated(ServerSocketThread thread, ServerSocket server);

    void onSocketAccepted(ServerSocketThread thread, Socket socket);
    void onAcceptTimeout(ServerSocketThread thread, ServerSocket server);

    void onServerThreadException(ServerSocketThread thread, Exception e);
    void onServerThreadStop(ServerSocketThread thread);

}
