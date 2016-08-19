import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class Server extends Thread {

    public static ServerDatabase serverDatabase; //used for store relevant info
    public static ServerSocket receiverServerSocket; //welcome socket
    private final Socket receiverSocket;  //connection socket

    public Server(Socket receiverSocket) {
        this.receiverSocket = receiverSocket;
    }

    public static void main(String[] args) throws IOException {
        checkArgs(args);
        int listenPort = Integer.valueOf(args[0]);
        receiverServerSocket = new ServerSocket(listenPort);
        serverDatabase = new ServerDatabase();

        attachShutDownHook();
        new LiveClientCheck(serverDatabase).start();  //check the heart beat report
        while (true) {
            Socket receiverSocket = receiverServerSocket.accept();
            Thread server = new Server(receiverSocket);
            server.start();
        }
    }

    @Override
    public void run() {
        // test
        System.out.println("=============================");
        String senderIP = receiverSocket.getInetAddress().getHostAddress();
        int senderPort = receiverSocket.getPort();
        System.out.println("Accept connection from IP: " + senderIP + ", Port: " + String.valueOf(senderPort));

        try {
            String request = SocketWrapper.receiveData(receiverSocket);

            // test
            System.out.println("[DEBUG] Receive request: " + request);

            if (request != null && !request.isEmpty()) {
                ServerHandler serverHandler = new ServerHandler(receiverSocket, serverDatabase);
                handleClientRequest(serverHandler, request);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // test
        if (receiverSocket.isClosed()) {
            System.out.println("A connection is closed.");
        }
        System.out.println("=============================");
    }
/*used for parse the command and handle different request from users*/
    private void handleClientRequest(ServerHandler serverHandler, String request) throws IOException {
        String[] commands = request.split("\\|");
        int commandsLength = commands.length;
        if (commandsLength == 4 && CommandType.AUTHENTICATE.equals(commands[0].trim())) {
            String username = commands[1];
            String password = commands[2];
            int clientListenPort = Integer.valueOf(commands[3]);
            serverHandler.doClientAuthentication(username, password, clientListenPort);
        } else if (commandsLength == 4 && CommandType.MESSAGE.equals(commands[0].trim())) {
            String username = commands[1];
            String targetUser = commands[2];
            String messageContent = commands[3];
            serverHandler.doClientMessage(username, targetUser, messageContent);
        } else if (commandsLength == 2 && CommandType.ONLINE.equals(commands[0].trim())) {
            String username = commands[1];
            serverHandler.doClientOnline(username);
        } else if (commandsLength == 3 && CommandType.BLOCK.equals(commands[0])) {
            String username = commands[1];
            String blockedUser = commands[2];
            serverHandler.doClientBlock(username, blockedUser);
        } else if (commandsLength == 3 && CommandType.UNBLOCK.equals(commands[0])) {
            String username = commands[1];
            String unblockedUser = commands[2];
            serverHandler.doClientUnblock(username, unblockedUser);
        } else if (commandsLength == 3 && CommandType.BROADCAST.equals(commands[0])) {
            String username = commands[1];
            String broadcastContent = commands[2];
            serverHandler.doClientBroadcast(username, broadcastContent);
        } else if (commandsLength == 2 && CommandType.LOGOUT.equals(commands[0])) {
            String username = commands[1];
            serverHandler.doClientLogout(username);
        } else if (commandsLength == 2 && CommandType.HEARTBEAT.equals(commands[0])) {
            String username = commands[1];
            serverHandler.doClientHeartbeat(username);
        } else if (commandsLength == 3 && CommandType.GET_ADDRESS.equals(commands[0])) {
            String username = commands[1];
            String tartgetUser = commands[2];
            serverHandler.doClientGetAddress(username, tartgetUser);
        }
    }

    private static void attachShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutDown();
            }
        });
    }
/*shut down the Server*/
    private static void shutDown() {
        System.out.println("Server is shutting down, online users will be logged out ...");
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        String data = String.format("%s\n", CommandType.SERVER_UNAVAILABLE);
        for (Map.Entry<String, ClientInfo> entry : clientInfoMap.entrySet()) {
            ClientInfo clientInfo = entry.getValue();
            if (clientInfo.isOnline()) {
                try {
                    ServerHandler.sendClientData(clientInfo, data);
                } catch (Exception e) { // do nothing
                }
            }
        }
    }
/*check args*/
    private static void checkArgs(String[] args) {
        if (args.length != 1) {
            throw new RuntimeException("Invalid command, please follow 'java Server <port number>'.");
        }
    }

}
