package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import Worker.WorkerInfo;

public class MasterServer {
    // Port for the Master server
    private static final int MASTER_PORT = 12345;
    // List of worker nodes
    private static List<WorkerInfo> workerList = new ArrayList<>();

    public static void main(String[] args) {
        // For example, we define one worker node on localhost port 6000.
        workerList.add(new WorkerInfo("localhost", 6000));

        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                // Create a new ClientHandler for each connection
                ClientHandler clientHandler = new ClientHandler(clientSocket, workerList);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
