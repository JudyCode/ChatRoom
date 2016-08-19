import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Random;

public class Client { //Client side

    public static final BufferedReader INPUT_READER = new BufferedReader(new InputStreamReader(System.in));

    private static boolean isLoggedIn = false;
    private static ServerSocket clientServerSocket;
    private static ClientHandler clientHandler;
    private static ClientHeartbeat clientHeartbeat;
    private static Thread clientListenerThread;

    public static void main(String[] args) throws UnknownHostException, IOException {
        checkArgs(args);
        initialization(args);

        attachShutDownHook();
        login();
        clientListenerThread.start();
        clientHeartbeat.start();
        chat();
    }

    private static void chat() throws IOException {  //receive the message and parse the command
        while (true) {
            String input = INPUT_READER.readLine().trim();
            String commands[] = input.split(" +");
            if (commands.length >= 3 && CommandType.MESSAGE.equals(commands[0])) {
                String targetUser = commands[1];
                String messageContent = input.replace(commands[0], "").replace(targetUser, "").trim();
                clientHandler.doMessage(targetUser, messageContent);
            } else if (commands.length == 1 && CommandType.ONLINE.equals(commands[0])) {
                clientHandler.doOnline();
            } else if (commands.length == 2 && CommandType.BLOCK.equals(commands[0])) {
                String blockedUser = commands[1];
                clientHandler.doBlock(blockedUser);
            } else if (commands.length == 2 && CommandType.UNBLOCK.equals(commands[0])) {
                String unblockedUser = commands[1];
                clientHandler.doUnblock(unblockedUser);
            } else if (commands.length >= 2 && CommandType.BROADCAST.equals(commands[0])) {
                String broadcastContent = input.replace(commands[0], "").trim();
                clientHandler.doBroadcast(broadcastContent);
            } else if (commands.length == 2 && CommandType.GET_ADDRESS.equals(commands[0])) {
                String targetUser = commands[1];
                clientHandler.doGetAddress(targetUser);
            } else if (commands.length >= 3 && CommandType.PRIVATE.equals(commands[0])) {
                String targetUser = commands[1];
                String messageContent = input.replace(commands[0], "").replace(targetUser, "").trim();
                clientHandler.doPrivate(targetUser, messageContent);
            } else if (commands.length == 1 && CommandType.LOGOUT.equals(commands[0])) {
                clientHandler.doLogout();
                isLoggedIn = false;
                System.exit(0);
            } else if (commands.length == 2
                    && (Constants.AGREE.equals(commands[0]) || Constants.DISAGREE.equals(commands[0])))
            { // bonus part 1.1, P2P Privacy and Consent, (dis)agreement on the private request.
                String fromUser = commands[1];
                ClientHandler.setDecisionOfP2pRequests(fromUser, commands[0]);
            } else {
                System.out.println("Invalid input: " + input);
            }
        }
    }

    private static void initialization(String[] args) throws IOException {
        String serverIP = args[0];
        int serverPort = Integer.valueOf(args[1]);
        int clientListenPort = 0;
        if (args.length == 3) {
            clientListenPort = Integer.valueOf(args[2]);
            clientServerSocket = new ServerSocket(clientListenPort);
        } else {
            Random random = new Random();
            while (clientServerSocket == null) {
                // pick an available port from [2000, 6999]
                clientListenPort = random.nextInt(5000) + 2000;
                try {
                    clientServerSocket = new ServerSocket(clientListenPort);
                } catch (IOException ex) {
                    clientServerSocket = null;
                }
            }
        }
        clientHandler = new ClientHandler(serverIP, serverPort, clientListenPort);
        clientHeartbeat = new ClientHeartbeat(serverIP, serverPort);
        clientListenerThread = createThreadToRunListener();
    }

    // login authentication, assume no spaces at the start or end of username and password
    private static void login() throws UnknownHostException, IOException {
        System.out.print("Username: ");
        String username = INPUT_READER.readLine().trim();
        AuthenticationStatus authStatus = AuthenticationStatus.UNKOWN;
        while (!AuthenticationStatus.PASS.equals(authStatus)) {
            System.out.print("Password: ");
            String password = INPUT_READER.readLine().trim();
            authStatus = clientHandler.doAuthentication(username, password);
        }
        clientHeartbeat.setUsername(username);
        isLoggedIn = true;
    }

    private static void attachShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutDown();
            }
        });
    }

    private static void shutDown() {
        try {
            if (isLoggedIn) {
                clientHandler.doLogout();
            }
        } catch (Exception e) {// do nothing
        }
    }

    private static Thread createThreadToRunListener() {
        return new Thread() {
            public void run() {
                try {
                    ClientListener.listen(clientServerSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private static void checkArgs(String[] args) {
        if (args.length != 2 && args.length != 3) {
            throw new RuntimeException("Invalid command, " +
                    "please follow 'java Client <server ip> <server port number> <(optional) client port number>'.");
        }
    }

}
