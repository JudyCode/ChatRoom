import java.io.IOException;
import java.net.Socket;

public class ClientHeartbeat extends Thread {//used for connecting with server and report heat beats every 30 seconds

    private final String serverIP;
    private final int serverPort;
    private String username;

    public ClientHeartbeat(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void run() {
        while (true) {
            try {
                sendHeartbeatSignal();
            } catch (IOException e) {
                // System.out.println("IOException when send heart-beat in ClientHeartbeat: " + e);
            }

            try {
                Thread.sleep(Constants.HEARTBEAT_TIME_IN_SECONDS * 1000);
            } catch (InterruptedException e) {
                // System.out.println("InterruptedException when sleep in ClientHeartbeat: " + e);
            }
        }

    }

    private void sendHeartbeatSignal() throws IOException {
        Socket senderSocket = new Socket(serverIP, serverPort);
        String request = String.format("%s|%s\n", CommandType.HEARTBEAT, username);
        SocketWrapper.sendData(senderSocket, request);
        SocketWrapper.closeSocket(senderSocket);
    }

}
