package Master;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // Thread-safe list for worker sockets.
    public static List<Socket> workerSockets = Collections.synchronizedList(new ArrayList<>());
    // A counter for worker registrations.
    private static int workerCount = 0;
    public static final Object workerAvailable = new Object();

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
                    writer.println("WORKER_ASSIGN:" + assignedId + ":" + workerCount);
                    synchronized (workerAvailable) {
                        workerSockets.add(socket);
                        workerAvailable.notifyAll();
                        // Broadcast a RELOAD command to all workers.
                        int currentWorkers = workerSockets.size();
                        for (Socket s : workerSockets) {
                            try {
                                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                                out.println("RELOAD");
                                out.println(String.valueOf(currentWorkers));
                            } catch (IOException ex) {
                                System.err.println("Error sending reload command: " + ex.getMessage());
                            }
                        }
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
}
