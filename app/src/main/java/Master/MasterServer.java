package Master;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import model.Store;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // Thread-safe list for worker sockets.
    public static List<Socket> workerSockets = Collections.synchronizedList(new ArrayList<>());
    // A counter for worker registrations.
    private static int workerCount = 0;
    public static final Object workerAvailable = new Object();
    public static final List<Store> dynamicStores = Collections.synchronizedList(new ArrayList<>());

    // Global container for reduce results keyed by command.
    public static final Map<String, String> pendingReduceResults = new HashMap<>();
    // Lock object for synchronizing reduce result waiting.
    public static final Object reduceLock = new Object();

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(MASTER_PORT);
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                socket.setSoTimeout(1000); // short timeout to read handshake

                // Read the first line to determine connection type.
                String firstLine = reader.readLine();
                if ("WORKER_HANDSHAKE".equals(firstLine)) {
                    int assignedId;
                    synchronized (MasterServer.class) {
                        assignedId = workerCount;
                        workerCount++;
                    }
                    // Tell the newcomer its slot and live count
                    writer.println("WORKER_ASSIGN:" + assignedId + ":" + workerCount);

                    // REPLAY any stores that were added dynamically before this worker arrived
                    //    (so it builds the same allStores list as the veterans)
                    synchronized (dynamicStores) {
                        Gson gson = new Gson();
                        for (Store s : dynamicStores) {
                            writer.println("ADD_STORE");
                            writer.println(gson.toJson(s));
                        }
                    }

                    // Now add it to the live list and trigger the partitioning reload
                    synchronized (workerAvailable) {
                        workerSockets.add(socket);
                        workerAvailable.notifyAll();
                        broadcastReload();
                    }

                    System.out.println("Worker assigned ID " + assignedId + " from " + socket.getInetAddress());
                }

                else if ("REDUCE_RESULT".equals(firstLine)) {
                    String command = reader.readLine();
                    String aggregatedResult = reader.readLine();
                    System.out.println("Received REDUCE_RESULT for command: " + command);
                    System.out.println("Aggregated result: " + aggregatedResult);
                    synchronized (reduceLock) {
                        pendingReduceResults.put(command, aggregatedResult);
                        reduceLock.notifyAll();
                    }
                    writer.println("ACK");
                    socket.close();
                }
                else {
                    ActionForClients clientHandler = new ActionForClients(socket, workerSockets, firstLine, reader);
                    new Thread(clientHandler).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Master Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException e) { }
            }
        }
    }

    /**
     * Instructs all connected workers to reload their store partitions.
     */
    public static void broadcastReload() {
        synchronized (workerAvailable) {
            int currentWorkers = workerSockets.size();
            for (Socket s : workerSockets) {
                try {
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println("RELOAD");
                    out.println(currentWorkers);
                } catch (IOException ex) {
                    System.err.println("Error sending reload command: " + ex.getMessage());
                }
            }
        }
    }
}
