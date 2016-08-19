import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ServerHandler {  //used for handle different command

    private final Socket receiverSocket;
    private final ServerDatabase serverDatabase;

    public ServerHandler(Socket receiverSocket, ServerDatabase serverDatabase) {
        this.receiverSocket = receiverSocket;
        this.serverDatabase = serverDatabase;
    }

    public void doClientAuthentication(String username, String password, int clientListenPort) throws IOException {
        AuthenticationStatus authStatus = AuthenticationStatus.UNKOWN;
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        if (!clientInfoMap.containsKey(username)) {
            authStatus = AuthenticationStatus.NO_SUCH_USER;
        } else {
            ClientInfo clientInfo = clientInfoMap.get(username);
            if (isBlockedClient(clientInfo)) {
                authStatus = AuthenticationStatus.BLOCKED;
            } else {
                authStatus = getAuthStatusOfUnblockedClient(clientInfo, username, password, clientListenPort);
            }
        }
        String serverResponseToClient = String.format("%s|%s\n", CommandType.AUTHENTICATE, authStatus.toString());
        sendClientResponse(serverResponseToClient);

        if (AuthenticationStatus.PASS.equals(authStatus)) {
            doPresenceBroadcast(CommandType.LOGIN_BROADCAST, username);
            sendOfflineMessages(username);
        }
    }

    public void doClientMessage(String username, String targetUser, String messageContent) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        String status = null;
        if (!clientInfoMap.containsKey(targetUser)) {
            status = Constants.NO_SUCH_USER;
        } else {
            ClientInfo targetClientInfo = clientInfoMap.get(targetUser);
            status = targetClientInfo.hasBlockedUser(username) ? Constants.BLOCKED : Constants.OK;
        }
        String serverResponseToClient = String.format("%s|%s|%s\n", CommandType.MESSAGE, status, targetUser);
        sendClientResponse(serverResponseToClient);

        if (Constants.OK.equals(status)) {
            if (clientInfoMap.get(targetUser).isOnline()) {
                String data = String.format("%s|%s|%s|%s\n", CommandType.MESSAGE, targetUser, username, messageContent);
                try {
                    sendClientData(clientInfoMap.get(targetUser), data);
                } catch (IOException e) {
                    // bonus part 2
                    serverDatabase.addOfflineMessage(targetUser, username, messageContent);
                    System.out.println("Fail to send message to target user due to connection problem," +
                            " message is saved as offline message.");
                }
            } else {
                serverDatabase.addOfflineMessage(targetUser, username, messageContent);
            }
        }
    }

    public void doClientOnline(String username) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        List<String> otherOnlineUsers = new ArrayList<String>();
        for (Map.Entry<String, ClientInfo> entry : clientInfoMap.entrySet()) {
            if (entry.getValue().isOnline() && (!entry.getKey().equals(username))) {
                otherOnlineUsers.add(entry.getKey());
            }
        }
        sendClientResponse(getOnlineResponseData(otherOnlineUsers));

    }
// Block
    public void doClientBlock(String username, String blockedUser) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        String status = Constants.OK;
        if (clientInfoMap.containsKey(blockedUser)) {
            clientInfoMap.get(username).blockUser(blockedUser);
        } else {
            status = Constants.NO_SUCH_USER;
        }
        String serverResponseToClient = String.format("%s|%s\n", CommandType.BLOCK, status);
        sendClientResponse(serverResponseToClient);
    }
//unblock
    public void doClientUnblock(String username, String unblockedUser) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        String status = Constants.OK;
        if (clientInfoMap.containsKey(unblockedUser)) {
            clientInfoMap.get(username).unblockUser(unblockedUser);
        } else {
            status = Constants.NO_SUCH_USER;
        }
        String serverResponseToClient = String.format("%s|%s\n", CommandType.UNBLOCK, status);
        sendClientResponse(serverResponseToClient);
    }
//broadcast
    public void doClientBroadcast(String username, String broadcastContent) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        List<ClientInfo> broadcastUsers = new ArrayList<ClientInfo>();
        boolean blocked = false;
        for (Map.Entry<String, ClientInfo> entry : clientInfoMap.entrySet()) {
            if (entry.getKey().equals(username)) {
                continue;
            }
            ClientInfo clientInfo = entry.getValue();
            if (clientInfo.isOnline()) {
                if (!clientInfo.hasBlockedUser(username)) {
                    broadcastUsers.add(clientInfo);
                } else {
                    blocked = true;
                }
            }
        }
        String serverResponseToClient = String.format("%s|%s\n",
                CommandType.BROADCAST, blocked ? Constants.BLOCKED : Constants.OK);
        sendClientResponse(serverResponseToClient);

        String data = String.format("%s|%s|%s\n", CommandType.BROADCAST, username, broadcastContent);
        for (ClientInfo clientInfo : broadcastUsers) {
            sendClientData(clientInfo, data);
        }
    }
//logout
    public void doClientLogout(String username) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        if (clientInfoMap.containsKey(username)) {
            clientInfoMap.get(username).setOnlineField(false);
        }
        String serverResponseToClient = String.format("%s|%s\n", CommandType.LOGOUT, Constants.OK);
        sendClientResponse(serverResponseToClient);

        doPresenceBroadcast(CommandType.LOGOUT_BROADCAST, username);
    }
//heartbeat
    public void doClientHeartbeat(String username) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        if (clientInfoMap.containsKey(username)) {
            clientInfoMap.get(username).setLastestHeartbeatTime(System.currentTimeMillis());
        }
        SocketWrapper.closeSocket(receiverSocket);
    }
//getaddress
    public void doClientGetAddress(String username, String targetUser) throws IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        String status = Constants.UNKNOWN, ipAddress = Constants.UNKNOWN, port = Constants.UNKNOWN;
        if (clientInfoMap.containsKey(targetUser)) {
            ClientInfo clientInfo = clientInfoMap.get(targetUser);
            if (!clientInfo.isOnline()) {
                status = Constants.OFFLINE;
            } else if (clientInfo.hasBlockedUser(username)) { // bonus part 1.2
                status = Constants.BLOCKED;
            } else {
                // bonus part 1.1
                try {
                    String request = String.format("%s|%s\n", CommandType.P2P_REQUEST, username);
                    String response = askPrivateAgreement(clientInfo, request);
                    if (Constants.AGREE.equals(response)) {
                        status = Constants.AGREE;
                        ipAddress = clientInfo.getIpAddress();
                        port = String.valueOf(clientInfo.getListenPort());
                    } else if (Constants.DISAGREE.equals(response)) {
                        status = Constants.DISAGREE;
                    }
                } catch (IOException e) {
                    // do nothing, status will be Constants.UNKNOWN.
                }
            }
        } else {
            status = Constants.NO_SUCH_USER;
        }
        String serverResponseToClient = String.format("%s|%s|%s|%s|%s\n",
                CommandType.GET_ADDRESS, status, targetUser, ipAddress, port);
        sendClientResponse(serverResponseToClient);
    }
//log in and log out broadcast
    private void doPresenceBroadcast(String presenceBroadcast, String username)
            throws UnknownHostException, IOException {
        Map<String, ClientInfo> clientInfoMap = serverDatabase.getClientInfoMap();
        for (Map.Entry<String, ClientInfo> entry : clientInfoMap.entrySet()) {
            if (entry.getKey().equals(username)) {
                continue;
            }
            ClientInfo clientInfo = entry.getValue();
            if (clientInfo.isOnline() && (!clientInfo.hasBlockedUser(username))) {
                String data = String.format("%s|%s\n", presenceBroadcast, username);
                sendClientData(clientInfo, data);
            }
        }
    }
//offline message
    private void sendOfflineMessages(String username) throws UnknownHostException, IOException {
        List<String> offlineMessages = serverDatabase.getOfflineMessages(username);
        if (offlineMessages == null || offlineMessages.size() == 0) {
            return;
        }
        StringBuilder data = new StringBuilder();
        data.append(CommandType.OFFLINE_MESSAGE);
        for (String offlineMessage : offlineMessages) {
            data.append("|" + offlineMessage);
        }
        sendClientData(serverDatabase.getClientInfoMap().get(username), data.toString());
        serverDatabase.clearOfflineMessages(username);
    }
//login block
    private boolean isBlockedClient(ClientInfo clientInfo) {
        if (clientInfo.getFailedLoginAttempts() >= Constants.LOGIN_MAX_FAILED_ATTEMPTS) {
            if (!passBlockTime(clientInfo.getLoginBlockStartTime(), System.currentTimeMillis())) {
                return true;
            } else {
                clientInfo.setFailedLoginAttempts(0); // reset number of failed attempts
                return false;
            }
        }
        return false;
    }

    private boolean passBlockTime(long blockStartTime, long currentTime) {
        return (currentTime - blockStartTime) > Constants.LOGIN_BLOCK_TIME_IN_SECONDS * 1000;
    }
//Authentication
    private AuthenticationStatus getAuthStatusOfUnblockedClient(ClientInfo clientInfo, String username,
            String password,
            int clientListenPort) throws UnknownHostException, IOException {
        AuthenticationStatus authStatus = AuthenticationStatus.UNKOWN;
        if (serverDatabase.isValidCredential(username, password)) {
            authStatus = AuthenticationStatus.PASS;
            if (clientInfo.isOnline()) {
                // if the user is already online, he/she will be notified and disconnected.
                sendClientData(clientInfo, CommandType.LOGOUT_MULTIPLE_LOGIN + "\n");
                // wait for some time to let the existed client disconnect.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) { // do nothing
                }
            }
            clientInfo.setOnlineField(true);
            clientInfo.setFailedLoginAttempts(0);
            String clientIP = receiverSocket.getInetAddress().getHostAddress();
            clientInfo.setIpAddress(clientIP);
            clientInfo.setListenPort(clientListenPort);
        } else {
            int failedAttemps = clientInfo.getFailedLoginAttempts() + 1;
            if (failedAttemps < Constants.LOGIN_MAX_FAILED_ATTEMPTS) {
                authStatus = AuthenticationStatus.INVALID;
            } else if (failedAttemps == Constants.LOGIN_MAX_FAILED_ATTEMPTS) {
                authStatus = AuthenticationStatus.INVALID_TO_BLOCKED;
                clientInfo.setLoginBlockStartTime(System.currentTimeMillis());
            }
            clientInfo.setFailedLoginAttempts(failedAttemps);
        }
        return authStatus;
    }

    private String getOnlineResponseData(List<String> otherOnlineUsers) {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(CommandType.ONLINE);
        if (otherOnlineUsers.size() > 0) {
            Collections.sort(otherOnlineUsers);
            for (String user : otherOnlineUsers) {
                strBuilder.append("|" + user);
            }
        }
        strBuilder.append("\n");
        return strBuilder.toString();
    }

    public static void sendClientData(ClientInfo clientInfo, String data) throws UnknownHostException, IOException {
        String clientIP = clientInfo.getIpAddress();
        int clientListenPort = clientInfo.getListenPort();
        Socket senderSocket = new Socket(clientIP, clientListenPort);
        SocketWrapper.sendData(senderSocket, data);
        SocketWrapper.closeSocket(senderSocket);
    }

    private static String askPrivateAgreement(ClientInfo clientInfo, String request) throws IOException {
        Socket senderSocket = new Socket(clientInfo.getIpAddress(), clientInfo.getListenPort());
        SocketWrapper.sendData(senderSocket, request);
        String response = SocketWrapper.receiveData(senderSocket);
        SocketWrapper.closeSocket(senderSocket);
        return response;
    }

    private void sendClientResponse(String serverResponseToClient) throws IOException {
        // test
        System.out.print("[DEBUG] Send  response: " + serverResponseToClient);

        SocketWrapper.sendData(receiverSocket, serverResponseToClient);
        SocketWrapper.closeSocket(receiverSocket);// Close the receiver socket after sending out the response to client.
    }

}
