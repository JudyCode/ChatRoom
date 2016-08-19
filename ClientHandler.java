import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler {

    private final String serverIP;
    private final int serverPort;
    private final int clientListenPort;
    private String username;
    private Map<String, String> userAddresses; // local data of IP addresses on client side
    private static Map<String, String> decisionOfP2pRequests; // Map<user-name of private request, agree or disagree>

    public ClientHandler(String serverIP, int serverPort, int clientListenPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.clientListenPort = clientListenPort;
        userAddresses = new HashMap<String, String>();
        decisionOfP2pRequests = new HashMap<String, String>();
    }

    public static void setDecisionOfP2pRequests(String fromUser, String decision) {
        decisionOfP2pRequests.put(fromUser, decision);
    }

    public static String getDecisionOfP2pRequests(String username) {
        if (decisionOfP2pRequests.containsKey(username)) {
            return decisionOfP2pRequests.get(username);
        }
        return Constants.UNKNOWN;
    }

    public AuthenticationStatus doAuthentication(String username, String password)
            throws UnknownHostException, IOException {
        String request = getAuthRequestString(username, password);
        AuthenticationStatus authStatus = getAuthStatus(getResponseFromServer(request));
        handleAuthResponse(authStatus);
        if (AuthenticationStatus.PASS.equals(authStatus)) {
            this.username = username;
        }
        return authStatus;
    }

    public void doMessage(String targetUser, String messageContent) throws UnknownHostException, IOException {
        if (targetUser.equals(username)) {
            System.out.println("No need to send message to yourself :)");
            return;
        }
        String request = String.format("%s|%s|%s|%s\n", CommandType.MESSAGE, username, targetUser, messageContent);
        handleMessageResponse(getResponseFromServer(request));
    }

    public void doOnline() throws IOException {
        String request = String.format("%s|%s\n", CommandType.ONLINE, username);
        handleOnlineResponse(getResponseFromServer(request));
    }

    public void doBlock(String blockedUser) throws IOException {
        if (blockedUser.equals(username)) {
            System.out.println("Can not block yourself.");
            return;
        }
        String request = String.format("%s|%s|%s\n", CommandType.BLOCK, username, blockedUser);
        handleBlockResponse(getResponseFromServer(request), blockedUser);
    }

    public void doUnblock(String unblockedUser) throws IOException {
        if (unblockedUser.equals(username)) {
            System.out.println("No need to unblock yourself.");
            return;
        }
        String request = String.format("%s|%s|%s\n", CommandType.UNBLOCK, username, unblockedUser);
        handleUnblockResponse(getResponseFromServer(request), unblockedUser);
    }

    public void doBroadcast(String broadcastContent) throws IOException {
        String request = String.format("%s|%s|%s\n", CommandType.BROADCAST, username, broadcastContent);
        handleBroadcastResponse(getResponseFromServer(request));
    }

    public void doGetAddress(String targetUser) throws IOException {
        if (targetUser.equals(username)) {
            System.out.println("No need to get address of yourself " +
                    "cause you don't need to set up p2p chat with yourself.");
            return;
        }
        String request = String.format("%s|%s|%s\n", CommandType.GET_ADDRESS, username, targetUser);
        handleGetAddressResponse(getResponseFromServer(request));
    }

    public void doPrivate(String targetUser, String messageContent) {
        if (targetUser.equals(username)) {
            System.out.println("No need to send private message to yourself.");
            return;
        }
        if (userAddresses.containsKey(targetUser)) {
            String[] split = userAddresses.get(targetUser).split("#");
            String ipAddress = split[0];
            int port = Integer.valueOf(split[1]);
            String data = String.format("%s|%s|%s\n", CommandType.PRIVATE, username, messageContent);
            try {
                sendPrivateMessage(ipAddress, port, data);
            } catch (IOException e) {
                // bonus part 2
                System.out.println("The user is no longer available at the previous address." +
                        " You can call 'getaddress <user>' to get latest address or send messages through Server.");
            }
        } else {
            System.out.println("You don't have the ip address info of the user, please use 'getaddress <user>'.");
        }
    }

    public void doLogout() throws IOException {
        String request = String.format("%s|%s\n", CommandType.LOGOUT, username);
        handleLogoutResponse(getResponseFromServer(request));
    }

    private void handleAuthResponse(AuthenticationStatus authStatus) {
        if (AuthenticationStatus.PASS.equals(authStatus)) {
            System.out.println("Welcome to simple chat server!");
        } else if (AuthenticationStatus.INVALID.equals(authStatus)) {
            System.out.println("Invalid Password. Please try again.");
        } else if (AuthenticationStatus.INVALID_TO_BLOCKED.equals(authStatus)) {
            System.out.println("Invalid Password. Your account has been blocked. Please try again after sometime.");
            System.exit(1);
        } else if (AuthenticationStatus.BLOCKED.equals(authStatus)) {
            System.out.println("Due to multiple login failures," +
                    " your account has been blocked. Please try again after sometime.");
            System.exit(1);
        } else if (AuthenticationStatus.NO_SUCH_USER.equals(authStatus)) {
            System.out.println("Invalid username, please try with correct username.");
            System.exit(1);
        } else {
            System.out.println("ERROR occurs when doing the authentication.");
            System.exit(1);
        }
    }

    private void handleMessageResponse(String response) {
        String[] split = response.trim().split("\\|");
        if (CommandType.MESSAGE.equals(split[0])) {
            if (Constants.BLOCKED.equals(split[1])) {
                System.out.println("Your message could not be delivered as the recipient has blocked you.");
            } else if (Constants.NO_SUCH_USER.equals(split[1])) {
                System.out.println("No such user: " + split[2]);
            }
        }
    }

    private void handleOnlineResponse(String response) {
        String[] split = response.trim().split("\\|");
        if (CommandType.ONLINE.equals(split[0])) {
            for (int i = 1; i < split.length; i++) {
                System.out.println(split[i]);
            }
            // remove this if you don't want to show this
            if (split.length == 1) {
                System.out.println("No other online users.");
            }
        }
    }

    private void handleBlockResponse(String response, String blockedUser) {
        String[] split = response.trim().split("\\|");
        if (CommandType.BLOCK.equals(split[0])) {
            if (Constants.OK.equals(split[1])) {
                System.out.println(String.format("User %s has been blocked", blockedUser));
            } else if (Constants.NO_SUCH_USER.equals(split[1])) {
                System.out.println("No such user: " + blockedUser);
            }
        }
    }

    private void handleUnblockResponse(String response, String unblockedUser) {
        String[] split = response.trim().split("\\|");
        if (CommandType.UNBLOCK.equals(split[0])) {
            if (Constants.OK.equals(split[1])) {
                System.out.println(String.format("User %s is unblocked", unblockedUser));
            } else if (Constants.NO_SUCH_USER.equals(split[1])) {
                System.out.println("No such user: " + unblockedUser);
            }
        }
    }

    private void handleBroadcastResponse(String response) {
        String[] split = response.trim().split("\\|");
        if (CommandType.BROADCAST.equals(split[0])) {
            if (Constants.BLOCKED.equals(split[1])) {
                System.out.println(String.format("Your message could not be delivered to some recipients"));
            }
        }
    }

    private void handleGetAddressResponse(String response) {
        String[] split = response.trim().split("\\|");
        if (CommandType.GET_ADDRESS.equals(split[0])) {
            String status = split[1];
            if (Constants.AGREE.equals(status)) {
                String user = split[2];
                String ipAddress = split[3];
                String port = split[4];
                userAddresses.put(user, ipAddress + "#" + port); // update local data
                System.out.println(String.format("User: %s, IP address: %s, port: %s", user, ipAddress, port));
            } else if (Constants.DISAGREE.equals(status) || Constants.UNKNOWN.equals(status)) {
                System.out.println("The target user doesn't agree on the private conversation (p2p chat)," +
                        " can not get the address.");
            } else if (Constants.OFFLINE.equals(status)) {
                System.out.println("The target user is offline.");
            } else if (Constants.BLOCKED.equals(status)) {
                System.out.println("Can not get the address because the target user has blocked you.");
            } else if (Constants.NO_SUCH_USER.equals(status)) {
                System.out.println("No such user.");
            }
        }
    }

    private void handleLogoutResponse(String response) {
        String[] split = response.trim().split("\\|");
        if (CommandType.LOGOUT.equals(split[0]) && Constants.OK.equals(split[1])) {
            // do nothing, remove the following line if you don't want to show the message
            System.out.println("You are logged out.");
        }
    }

    private String getResponseFromServer(String request) throws IOException {
        Socket senderSocket = new Socket(serverIP, serverPort);
        SocketWrapper.sendData(senderSocket, request);
        String response = SocketWrapper.receiveData(senderSocket);
        SocketWrapper.closeSocket(senderSocket);
        return response;
    }

    private void sendPrivateMessage(String ipAddress, int port, String data) throws IOException {
        Socket senderSocket = new Socket(ipAddress, port);
        SocketWrapper.sendData(senderSocket, data);
        SocketWrapper.closeSocket(senderSocket);
    }

    private String getAuthRequestString(String username, String password) {
        return String.format("%s|%s|%s|%s\n", CommandType.AUTHENTICATE, username, password, clientListenPort);
    }

    private AuthenticationStatus getAuthStatus(String response) {
        String[] split = response.trim().split("\\|");
        if (split.length == 2 && CommandType.AUTHENTICATE.equals(split[0].trim())) {
            return AuthenticationStatus.getAuthStatusFromStr(split[1].trim());
        }
        return AuthenticationStatus.UNKOWN;
    }

}
