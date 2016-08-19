import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDatabase {

    private final Map<String, String> credentialsMap; // Map<username, password>
    private final Map<String, ClientInfo> clientInfoMap;
    private final Map<String, List<String>> offlineMessages;

    public ServerDatabase() throws IOException {
        credentialsMap = new HashMap<String, String>();
        clientInfoMap = new ConcurrentHashMap<String, ClientInfo>();// ConcurrentHashMap for multiple thread safe.
        offlineMessages = new HashMap<String, List<String>>();
        initialization();
    }

    public Map<String, ClientInfo> getClientInfoMap() {
        return this.clientInfoMap;
    }

    public boolean isValidCredential(String username, String password) {
        if (credentialsMap.containsKey(username) && credentialsMap.get(username).equals(password)) {
            return true;
        }
        return false;
    }

    public void addOfflineMessage(String user, String fromUser, String offlineMessage) {
        if (offlineMessages.containsKey(user)) {
            offlineMessages.get(user).add(fromUser + "|" + offlineMessage);
        }
    }

    public List<String> getOfflineMessages(String user) {
        if (offlineMessages.containsKey(user)) {
            return offlineMessages.get(user);
        }
        return new ArrayList<String>();
    }

    public void clearOfflineMessages(String user) {
        if (offlineMessages.containsKey(user)) {
            offlineMessages.get(user).clear();
        }
    }

    private void initialization() throws IOException {
        String path = ServerDatabase.class.getClassLoader().getResource("credentials.txt").getPath();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(path));
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            String[] split = line.trim().split(" +");
            if (split.length == 2) {
                String username = split[0];
                String password = split[1];
                credentialsMap.put(username, password);
                clientInfoMap.put(username, new ClientInfo());
                offlineMessages.put(username, new ArrayList<String>());
            } else {
                System.out.println("Found a user credential not following 'username password' format: " + line);
            }
        }
        bufferedReader.close();
    }
}
