package Worker;

public class WorkerInfo {
    private String host;
    private int port;

    public WorkerInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}

