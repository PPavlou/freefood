package Master;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import com.google.gson.Gson;
import model.Store;

/**
 * MasterServer listens for worker and client connections,
 * manages worker registration and shutdown,
 * coordinates map-reduce operations,
 * and forwards commands between clients and workers.
 */
public class MasterServer {
    /** Port on which the MasterServer listens. */
    private static final int MASTER_PORT = 12345;

    // NEW – instead of storing raw Sockets, keep host+port per worker
    public static final Map<Integer,String> workerHostsById =
            Collections.synchronizedMap(new HashMap<>());
    public static final Map<Integer,Integer> workerPortsById =
            Collections.synchronizedMap(new HashMap<>());

    /** Thread-safe list of sockets for connected worker nodes. */
    public static List<Socket> workerSockets =
            Collections.synchronizedList(new ArrayList<>());

    /** Thread-safe map of worker IDs to their sockets. */
    public static final Map<Integer,Socket> workerSocketsById =
            Collections.synchronizedMap(new HashMap<>());

    /** Counter for assigning worker IDs. */
    private static int workerCount = 0;

    /** Monitor object for worker availability notifications. */
    public static final Object workerAvailable = new Object();

    /** List of stores added or removed dynamically, shared with new workers on registration. */
    public static final List<Store> dynamicStores =
            Collections.synchronizedList(new ArrayList<>());

    /** Names of stores removed dynamically, to replay REMOVE_STORE to late joiners. */
    public static final Set<String> dynamicRemoves =
            Collections.synchronizedSet(new HashSet<>());

    /** Map storing pending reduce results, keyed by command name. */
    public static final Map<String, String> pendingReduceResults = new HashMap<>();

    /** Lock object for synchronizing waiting for reduce results. */
    public static final Object reduceLock = new Object();

    /**
     * Entry point for the Master server.
     * Listens for incoming connections on the master port, handles:
     * worker handshakes, worker shutdown notifications,
     * reduce result submissions, and client commands.
     *
     * @param args command-line arguments (not used)
     */
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
                    // 1) read which port *this* worker will listen on:
                    String portLine = reader.readLine();
                    int workerPort = Integer.parseInt(portLine.trim());

                                // 2) assign ID
                    int assignedId;
                    synchronized (MasterServer.class) {
                        assignedId = workerCount++;
                    }
                    writer.println("WORKER_ASSIGN:" + assignedId + ":" + workerCount);

                    // REPLAY any stores that were added dynamically before this worker arrived
                    //    (so it builds the same allStores list as the veterans)
                    synchronized (dynamicStores) {
                        Gson gson = new Gson();
                        for (Store s : dynamicStores) {
                            String jobId=ActionForClients.generateJobId();
                            writer.println("ADD_STORE(REPLAY)");
                            writer.println(gson.toJson(s));
                            writer.println(jobId);
                        }
                    }

                    // REPLAY any stores that were removed dynamically before this worker arrived
                    synchronized (dynamicRemoves) {
                        for (String storeName : dynamicRemoves) {
                            String jobId=ActionForClients.generateJobId();
                            writer.println("REMOVE_STORE(REPLAY)");
                            writer.println(storeName);
                            writer.println(jobId);
                        }
                    }

                    String host = socket.getInetAddress().getHostAddress();
                    synchronized(MasterServer.class) {
                        workerHostsById.put(assignedId, host);
                        workerPortsById.put(assignedId, workerPort);
                    }
                    socket.close();

                    // 6) notify all workers to reload their partitions
                    broadcastReload();

                    System.out.printf(
                            "Worker assigned ID %d at %s:%d%n",
                            assignedId, host, workerPort
                    );

                    continue;
                }
                else if (firstLine != null && (firstLine.contains("WORKER_SHUTDOWN:"))) {
                    // Expect the format: WORKER_SHUTDOWN:<id>
                    String[] parts = firstLine.split(":");
                    if (parts.length != 2) {
                        System.err.println("Invalid WORKER_SHUTDOWN message: " + firstLine);
                        continue;
                    }
                    int id = Integer.parseInt(parts[1]);

                    // decrement global worker count
                    synchronized (MasterServer.class) {
                        workerCount--;
                    }
                    System.out.println("WORKER_SHUTDOWN – ID=" + id + "  CURRENT WORKER COUNT: " + workerCount);

                    synchronized (workerAvailable) {
                        // look up the original Socket you stored for this ID
                        Socket toRemove = workerSocketsById.remove(id);
                        if (toRemove != null) {
                            workerSockets.remove(toRemove);
                            System.out.println("Removed worker " + id + " at " + toRemove.getRemoteSocketAddress());
                            try {
                                toRemove.close();
                            } catch (IOException ex) {
                                System.err.println("Error closing socket for worker " + id + ": " + ex.getMessage());
                            }
                        } else {
                            System.err.println("No socket found for WORKER_SHUTDOWN ID=" + id);
                        }
                        shiftWorkerIdsDown(id);
                        workerAvailable.notifyAll();
                        broadcastReload();
                    }
                }
                else if ("REDUCE_RESULT".equals(firstLine)) {
                    String jobId = reader.readLine();
                    String command = reader.readLine();
                    String aggregatedResult = reader.readLine();
                    System.out.println("Received REDUCE_RESULT for jobId: " + jobId +
                            ", command: " + command);
                    System.out.println("Aggregated result: " + aggregatedResult);
                    synchronized (reduceLock) {
                        pendingReduceResults.put(command + "|" + jobId, aggregatedResult);
                        reduceLock.notifyAll();
                    }
                    writer.println("ACK");
                    socket.close();
                }
                else {
                    ActionForClients clientHandler = new ActionForClients(socket, firstLine, reader);
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
        int currentWorkers = workerHostsById.size();
        for (int id : workerHostsById.keySet()) {
            String host = workerHostsById.get(id);
            int    port = workerPortsById.get(id);
            try (Socket ws = new Socket(host, port);
                 PrintWriter out = new PrintWriter(ws.getOutputStream(), true)) {
                String jobId = ActionForClients.generateJobId();
                out.println("RELOAD");
                out.println(currentWorkers);
                out.println(jobId);
            } catch (IOException ex) {
                System.err.println("Reload → worker " + id + " failed: " + ex.getMessage());
            }
        }
    }


    /**
     * Broadcasts a message to specific worker replicas.
     *
     * @param workerIds list of worker IDs (primary + replicas) to send the message to
     * @param message   the message to send
     */
    public static void broadcastToReplicas(List<Integer> workerIds, String message) {
        synchronized (workerAvailable) {
            for (Integer workerId : workerIds) {
                Socket workerSocket = workerSocketsById.get(workerId);
                if (workerSocket != null && !workerSocket.isClosed()) {
                    try {
                        PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                        out.println(message);
                    } catch (IOException ex) {
                        System.err.println("Error sending to worker " + workerId + ": " + ex.getMessage());
                    }
                } else {
                    System.err.println("Worker socket is null or closed for ID: " + workerId);
                }
            }
        }
    }

    /**
     * Shifts down worker IDs for all workers with IDs greater than the removed ID,
     * notifies them to decrement their internal ID, and updates the mapping.
     *
     * @param removedId the ID of the worker that was removed
     */
    private static void shiftWorkerIdsDown(int removedId) {
        Map<Integer, Socket> updatedAssignments = new HashMap<>();

        for (Integer oldId : new ArrayList<>(workerSocketsById.keySet())) {
            if (oldId > removedId) {
                Socket sock = workerSocketsById.remove(oldId);
                int newId = oldId - 1;
                updatedAssignments.put(newId, sock);
                try {
                    PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                    String jobId=ActionForClients.generateJobId();

                    out.println("DECREMENT_ID");
                    out.println(newId + ":" + workerCount);
                    out.println(jobId);
                    System.out.println("DECREASE ID FOR WORKER: " + oldId);
                } catch (IOException ex) {
                    System.err.println("Failed to notify worker " + oldId + " of new ID: " + ex.getMessage());
                }
            }
        }
        workerSocketsById.putAll(updatedAssignments);
    }
}
