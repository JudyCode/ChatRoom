import java.util.HashSet;
import java.util.Set;

public class ClientInfo {

    private boolean online;
    private int failedLoginAttempts;
    private long loginBlockStartTime;
    private String ipAddress;
    private int listenPort;
    private Set<String> blockedUsers;
    private long lastestHeartbeatTime;

    public ClientInfo() {
        blockedUsers = new HashSet<String>();
    }

    public boolean isOnline() {
        return this.online;
    }

    public void blockUser(String username) {
        blockedUsers.add(username);
    }

    public void unblockUser(String username) {
        blockedUsers.remove(username);
    }

    public boolean hasBlockedUser(String username) {
        return blockedUsers.contains(username);
    }

    public int getFailedLoginAttempts() {
        return this.failedLoginAttempts;
    }

    public long getLoginBlockStartTime() {
        return this.loginBlockStartTime;
    }

    public long getLastestHearbeatTime() {
        return this.lastestHeartbeatTime;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public int getListenPort() {
        return this.listenPort;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public void setLoginBlockStartTime(long loginBlockStartTime) {
        this.loginBlockStartTime = loginBlockStartTime;
    }

    public void setOnlineField(boolean online) {
        this.online = online;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public void setLastestHeartbeatTime(long lastestHeartbeatTime) {
        this.lastestHeartbeatTime = lastestHeartbeatTime;
    }

}
