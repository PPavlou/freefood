package Master;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.UUID;

import com.google.gson.Gson;
import model.Store;

/**
 * Handles incoming client connections, forwards commands to worker nodes,
 * collects and reduces their responses, and sends the final result back to the client.
 */
public class ActionForClients implements Runnable {
    private Socket clientSocket;
    private BufferedReader initialReader;
    private String firstLine;

    /**
     * Constructs a handler for a connected client.
     *
     * @param clientSocket   the socket connected to the client
     * @param firstLine      the first command line received from the client
     * @param initialReader  reader already positioned after reading the first line
     */
    public ActionForClients(Socket clientSocket,
                           String firstLine,
                            BufferedReader initialReader) {
        this.clientSocket  = clientSocket;
        this.firstLine     = firstLine;
        this.initialReader = initialReader;
    }

    /**
     * Generates a unique job ID for tracking distributed operations.
     *
     * @return a unique job ID string
     */
    public static String generateJobId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Entry point for the client handler thread.
     * Reads the full client command, forwards it to workers,
     * waits for or computes the reduced response, and writes it back.
     */
    @Override
    public void run() {
        try (BufferedReader reader = initialReader;
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String command = firstLine;
            String data    = reader.readLine();
            System.out.println("Master received command: " + command);
            System.out.println("Master received data: " + data);

            String jobId = generateJobId();
            System.out.println("Generated jobId: " + jobId + " for command: " + command);
            // forward to workers and gather their raw responses
            List<String> workerResponses = forwardToWorkers(command, data, jobId);

            Gson gson = new Gson();
            String finalResponse;

            // === CHANGED: only show one reply for ADD_STORE/REMOVE_STORE ===
            if ("ADD_STORE".equalsIgnoreCase(command) ||
                    "REMOVE_STORE".equalsIgnoreCase(command)) {

                // if any worker succeeded, just take the first response
                finalResponse = workerResponses.isEmpty()
                        ? "[]"
                        : workerResponses.get(0);

            } else {
                // existing reduce vs broadcast logic

                Set<String> reduceCommands = new HashSet<>(Arrays.asList(
                        "SEARCH", "AGGREGATE_SALES_BY_PRODUCT_NAME",
                        "LIST_STORES", "DELETED_PRODUCTS"
                ));

                if (reduceCommands.contains(command.toUpperCase())) {
                    String resultKey = command + "|" + jobId;
                    synchronized (MasterServer.reduceLock) {
                        while (!MasterServer.pendingReduceResults.containsKey(resultKey)) {
                            try {
                                MasterServer.reduceLock.wait(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                writer.println("{\"error\": \"Interrupted while waiting for reduce result.\"}");
                                return;
                            }
                        }
                        finalResponse = MasterServer.pendingReduceResults.remove(resultKey);
                    }
                } else {
                    finalResponse = gson.toJson(workerResponses);
                }
            }

            // send the (possibly single) response back to the manager console
            writer.println(finalResponse);

            // trigger reload if we just added or removed a store
            if ("ADD_STORE".equalsIgnoreCase(command) ||
                    "REMOVE_STORE".equalsIgnoreCase(command)) {

                if ("ADD_STORE".equalsIgnoreCase(command)) {
                    Store s = gson.fromJson(data, Store.class);
                    MasterServer.dynamicStores.add(s);

                } else { // REMOVE_STORE
                    String storeName = data.trim();
                    MasterServer.dynamicStores.removeIf(s -> s.getStoreName().equals(storeName));
                    MasterServer.dynamicRemoves.add(storeName);
                }

                MasterServer.broadcastReload();
            }

        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    /**
     * Forwards the given command and data to appropriate worker(s),
     * collects their responses, and returns the list of results.
     *
     * @param command the client command to forward
     * @param data    the payload for the command
     * @param jobId   the unique job identifier for this operation
     * @return list of responses from workers
     */
    private List<String> forwardToWorkers(String command, String data, String jobId) {
        List<String> responses = new ArrayList<>();

        Set<String> directedCommands = new HashSet<>(Arrays.asList(
                "ADD_PRODUCT", "REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT", "INCREMENT_PRODUCT_AMOUNT", "DECREMENT_PRODUCT_AMOUNT",
                "PURCHASE_PRODUCT", "REVIEW"
        ));

        if (directedCommands.contains(command.toUpperCase())) {
            // 1) Extract store name
            String storeName = extractStoreName(command, data);
            if (storeName == null || storeName.isEmpty()) {
                responses.add("Error: Cannot extract store name for command " + command);
                return responses;
            }

            // 2) Wait for at least one worker to register
            synchronized (MasterServer.workerAvailable) {
                while (MasterServer.workerHostsById.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responses.add("Error: Interrupted while waiting for a worker.");
                        return responses;
                    }
                }
            }

            int workerCount = MasterServer.workerHostsById.size();
            int primary     = Math.abs(storeName.hashCode()) % workerCount;

            // 3) Send to primary
            String resp = sendToWorker(primary, command, data, jobId);
            responses.add(resp);

            // 4) Replication fallback on error
            if (workerCount > 1 && resp.startsWith("{\"error\"")) {
                List<Integer> replicaIds = new ArrayList<>();
                int replicationFactor = 2;
                for (int r = 1; r < replicationFactor; r++) {
                    replicaIds.add((primary + r) % workerCount);
                }
                String payload = command + "|" + data + "|" + jobId;
                MasterServer.broadcastToReplicas(replicaIds, payload);
            }

        } else {
            // Broadcast to all workers
            synchronized (MasterServer.workerAvailable) {
                while (MasterServer.workerHostsById.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responses.add("Error: Interrupted while waiting for workers.");
                        return responses;
                    }
                }
            }

            for (Integer workerId : MasterServer.workerHostsById.keySet()) {
                String r = sendToWorker(workerId, command, data, jobId);
                responses.add(r);
            }
        }

        return responses;
    }



    private String sendToWorker(int workerId, String cmd, String data, String jobId) {
        String host = MasterServer.workerHostsById.get(workerId);
        int    port = MasterServer.workerPortsById.get(workerId);
        try (Socket s = new Socket(host, port);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println(cmd);
            out.println(data);
            out.println(jobId);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("CMD_RESPONSE:")) {
                    return line.substring("CMD_RESPONSE:".length());
                }
            }
        } catch (IOException e) {
            return "{\"error\":\"Worker comms failed\"}";
        }
        return "{\"error\":\"No response\"}";
    }


    /**
     * Extracts the target store name from the command and data payload,
     * using the expected format for each command type.
     *
     * @param command the client command name
     * @param data    the payload string (may contain storeName in various formats)
     * @return the extracted store name, or null if it cannot be determined
     */
    private String extractStoreName(String command, String data) {
        if ("ADD_STORE".equalsIgnoreCase(command)) {
            try {
                return new Gson().fromJson(data, Store.class).getStoreName();
            } catch (Exception e) {
                return null;
            }
        } else if ("REMOVE_STORE".equalsIgnoreCase(command)) {
            return data.trim();
        } else if ("REVIEW".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|");
            return parts.length >= 1 ? parts[0].trim() : null;
        } else if ("ADD_PRODUCT".equalsIgnoreCase(command)
                || "REMOVE_PRODUCT".equalsIgnoreCase(command)
                || "UPDATE_PRODUCT_AMOUNT".equalsIgnoreCase(command)
                || "INCREMENT_PRODUCT_AMOUNT".equalsIgnoreCase(command)
                || "DECREMENT_PRODUCT_AMOUNT".equalsIgnoreCase(command)
                || "PURCHASE_PRODUCT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 2);
            return parts.length >= 1 ? parts[0].trim() : null;
        }
        return null;
    }
}