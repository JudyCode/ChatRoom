import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ClientListener extends Thread { //used for listen the request from server and peers

    private final Socket receiverSocket;

    public ClientListener(Socket receiverSocket) {
        this.receiverSocket = receiverSocket;
    }

    public static void listen(ServerSocket clientServerSocket) throws IOException {
        while (true) {
	//used for p2p connection
            Socket receiverSocket = clientServerSocket.accept();
            Thread clientListener = new ClientListener(receiverSocket);
            clientListener.start();
        }
    }

    public void run() {
	//parse the command
        try {
            String data = SocketWrapper.receiveData(receiverSocket);
            if (data != null && !data.isEmpty()) {
                String[] commands = data.split("\\|");
                if (commands.length == 1 && CommandType.LOGOUT_MULTIPLE_LOGIN.equals(commands[0])) {
                    System.out.println("Your account is logged in from another place, you will be disconnected.");
                    System.exit(1);
                } else if (commands.length == 4 && CommandType.MESSAGE.equals(commands[0])) {
                    String fromUser = commands[2];
                    String message = commands[3];
                    System.out.println(String.format("%s: %s", fromUser, message));
                } else if (commands.length == 3 && CommandType.BROADCAST.equals(commands[0])) {
                    String fromUser = commands[1];
                    String broadcastMessage = commands[2];
                    System.out.println(String.format("%s: %s", fromUser, broadcastMessage));
                } else if (commands.length == 2 && CommandType.LOGIN_BROADCAST.equals(commands[0])) {
                    System.out.println(String.format("User %s is logged in.", commands[1]));
                } else if (commands.length == 2 && CommandType.LOGOUT_BROADCAST.equals(commands[0])) {
                    System.out.println(String.format("User %s is logged out.", commands[1]));
                } else if (commands.length >= 2 && CommandType.OFFLINE_MESSAGE.equals(commands[0])) {
                    System.out.println("Messages sent to you when you are not online: ");
                    for (int i = 1; i < commands.length; i = i + 2) {
                        System.out.println(String.format("%s: %s", commands[i], commands[i + 1]));
                    }
                } else if (commands.length == 1 && CommandType.SERVER_UNAVAILABLE.equals(commands[0])) {
                    System.out.println("Server is unavailable, you will be logged out.");
                    System.exit(1);
                } else if (commands.length == 3 && CommandType.PRIVATE.equals(commands[0])) {
                    String fromUser = commands[1];
                    String privateMessage = commands[2];
                    System.out.println(String.format("[private] %s: %s", fromUser, privateMessage));
                } else if (commands.length == 2 && CommandType.P2P_REQUEST.equals(commands[0])) {
                    String fromUser = commands[1];
                    handleP2pRequest(fromUser);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                SocketWrapper.closeSocket(receiverSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleP2pRequest(String fromUser) {
        ClientHandler.setDecisionOfP2pRequests(fromUser, Constants.UNKNOWN);
        System.out.println(String.format("User %s wants to get your ip address to setup p2p chat, " +
                "enter '%s %s' if you agree, or '%s %s' if you don't agree:",
                fromUser, Constants.AGREE, fromUser, Constants.DISAGREE, fromUser));
        try {
            String decision = getDecisionFromClient(fromUser);
            if (Constants.UNKNOWN.equals(decision)) {
                System.out.println("Timed out to give your decision (agree/disagree) on p2p request," +
                        " decision is UNKOWN, will not provide your ip address info to User " + fromUser);
            }
            SocketWrapper.sendData(receiverSocket, decision + "\n");
        } catch (IOException e) {
        }
    }

    private String getDecisionFromClient(String fromUser) {
        String decision = Constants.UNKNOWN;
        long timeStamp = System.currentTimeMillis();
        // apply a time-out to get the client's decision in case non-response for a long time
        while ((System.currentTimeMillis() - timeStamp) < Constants.TERMINAL_INPUT_TIMEOUT_IN_SECONDS * 1000)
        {
            decision = ClientHandler.getDecisionOfP2pRequests(fromUser);
            if (!Constants.UNKNOWN.equals(decision)) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {// do nothing
            }
        }
        return decision;
    }

}
