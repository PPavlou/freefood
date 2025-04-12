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
    // An object to synchronize waiting for reduce results.
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
                socket.setSoTimeout(1000); // short timeout to read the handshake

                // Read the first line to determine the connection type.
                String firstLine = reader.readLine();
                if ("WORKER_HANDSHAKE".equals(firstLine)) {
                    // Assign a worker ID and update count.
                    int assignedId;
                    synchronized (MasterServer.class) {
                        assignedId = workerCount;
                        workerCount++;
                    }
                    // Inform the worker of its assignment:
                    // Format: WORKER_ASSIGN:<assignedId>:<current total workers>
                    writer.println("WORKER_ASSIGN:" + assignedId + ":" + workerCount);
                    // Add the socket to the list and notify waiting threads.
                    synchronized (workerAvailable) {
                        workerSockets.add(socket);
                        workerAvailable.notifyAll();

                        // After adding a new worker, broadcast a RELOAD command to all workers.
                        int currentWorkers = workerSockets.size();
                        for (Socket s : workerSockets) {
                            try {
                                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                                out.println("RELOAD");
                                out.println(String.valueOf(currentWorkers)); // send updated total
                            } catch (IOException ex) {
                                System.err.println("Error sending reload command: " + ex.getMessage());
                            }
                        }
                    }
                    System.out.println("Worker assigned ID " + assignedId + " from " + socket.getInetAddress());
                }
                // Handle incoming connections from the Reduce server.
                else if ("REDUCE_RESULT".equals(firstLine)) {
                    // Read the next two lines: the command and its aggregated result.
                    String command = reader.readLine();
                    String aggregatedResult = reader.readLine();
                    System.out.println("Received REDUCE_RESULT for command: " + command);
                    System.out.println("Aggregated result: " + aggregatedResult);
                    // Store result in the global map and notify waiting threads.
                    synchronized (reduceLock) {
                        pendingReduceResults.put(command, aggregatedResult);
                        reduceLock.notifyAll();
                    }
                    writer.println("ACK");
                    socket.close();
                }
                // Otherwise, the connection is coming from a manager (client).
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
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore closing exceptions.
                }
            }
        }
    }
}
