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

import com.google.gson.Gson;
import model.Store;

/**
 * Handles incoming client connections, forwards commands to worker nodes,
 * collects and reduces their responses, and sends the final result back to the client.
 */
public class ActionForClients implements Runnable {
    private Socket clientSocket;
    private List<Socket> workerSockets;
    private BufferedReader initialReader;
    private String firstLine;

    /**
     * Constructs a handler for a connected client.
     *
     * @param clientSocket   the socket connected to the client
     * @param workerSockets  the list of sockets for available worker nodes
     * @param firstLine      the first command line received from the client
     * @param initialReader  reader already positioned after reading the first line
     */
    public ActionForClients(Socket clientSocket,
                            List<Socket> workerSockets,
                            String firstLine,
                            BufferedReader initialReader) {
        this.clientSocket  = clientSocket;
        this.workerSockets = workerSockets;
        this.firstLine     = firstLine;
        this.initialReader = initialReader;
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

            // forward to workers and gather their raw responses
            List<String> workerResponses = forwardToWorkers(command, data);

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
                    synchronized (MasterServer.reduceLock) {
                        while (!MasterServer.pendingReduceResults.containsKey(command)) {
                            try {
                                MasterServer.reduceLock.wait(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                writer.println("{\"error\": \"Interrupted while waiting for reduce result.\"}");
                                return;
                            }
                        }
                        finalResponse = MasterServer.pendingReduceResults.remove(command);
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
     * @return list of responses from workers
     */
    private List<String> forwardToWorkers(String command, String data) {
        List<String> responses = new ArrayList<>();

        Set<String> directedCommands = new HashSet<>(Arrays.asList(
                "ADD_PRODUCT", "REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT", "INCREMENT_PRODUCT_AMOUNT", "DECREMENT_PRODUCT_AMOUNT",
                "PURCHASE_PRODUCT", "REVIEW"
        ));

        if (directedCommands.contains(command.toUpperCase())) {
            // Send to the single worker responsible for this store
            String storeName = extractStoreName(command, data);
            if (storeName == null || storeName.isEmpty()) {
                responses.add("Error: Cannot extract store name for command " + command);
                return responses;
            }
            int workerCount;
            int index;
            Socket workerSocket;

            synchronized (MasterServer.workerAvailable) {
                while (workerSockets.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responses.add("Error: Interrupted while waiting for a worker.");
                        return responses;
                    }
                }
                workerCount = workerSockets.size();
                index       = Math.abs(storeName.hashCode()) % workerCount;
                workerSocket = workerSockets.get(index);
            }

            try {
                synchronized (workerSocket) {
                    PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(workerSocket.getInputStream())
                    );
                    out.println(command);
                    out.println(data);

                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("CMD_RESPONSE:")) {
                            responses.add(line.substring("CMD_RESPONSE:".length()));
                            break;
                        }
                    }

                    if (workerCount>1 && workerSocket.isClosed()) {
                        int replicationFactor = 2;
                        List<Integer> replicaIds = new ArrayList<>();
                        for (int r = 1; r < replicationFactor; r++) {
                            replicaIds.add((index + r) % workerCount);
                        }
                        // build the same “command|data” payload
                        String payload = command + "|" + data;

                        MasterServer.broadcastToReplicas(replicaIds, payload);
                    }
                }
            } catch (IOException e) {
                responses.add("Error with worker: " + e.getMessage());
            }

        } else {
            synchronized (MasterServer.workerAvailable) {
                while (workerSockets.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responses.add("Error: Interrupted while waiting for workers.");
                        return responses;
                    }
                }
            }
            for (Socket workerSocket : workerSockets) {
                try {
                    synchronized (workerSocket) {
                        PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(workerSocket.getInputStream())
                        );
                        out.println(command);
                        out.println(data);
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("CMD_RESPONSE:")) {
                                responses.add(line.substring("CMD_RESPONSE:".length()));
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    responses.add("Error with worker: " + e.getMessage());
                }
            }
        }

        return responses;
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
