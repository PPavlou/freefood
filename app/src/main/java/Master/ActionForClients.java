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

public class ActionForClients implements Runnable {
    private Socket clientSocket;
    private List<Socket> workerSockets;
    private BufferedReader initialReader;
    private String firstLine;

    public ActionForClients(Socket clientSocket, List<Socket> workerSockets, String firstLine, BufferedReader initialReader) {
        this.clientSocket = clientSocket;
        this.workerSockets = workerSockets;
        this.firstLine = firstLine;
        this.initialReader = initialReader;
    }

    @Override
    public void run() {
        try (BufferedReader reader = initialReader;
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String command = firstLine;
            String data = reader.readLine();
            System.out.println("Master received command: " + command);
            System.out.println("Master received data: " + data);

            List<String> workerResponses = forwardToWorkers(command, data);
            Gson gson = new Gson();
            String finalResponse;

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
                    finalResponse = MasterServer.pendingReduceResults.get(command);
                    MasterServer.pendingReduceResults.remove(command);
                }
            } else {
                finalResponse = gson.toJson(workerResponses);
            }

            // Send back to manager console
            writer.println(finalResponse);

            // Trigger workers to reload partitions on add/remove store
            if ("ADD_STORE".equalsIgnoreCase(command) || "REMOVE_STORE".equalsIgnoreCase(command)) {
                MasterServer.broadcastReload();
            }

        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

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
                int workerCountLocal = workerSockets.size();
                int index = Math.abs(storeName.hashCode()) % workerCountLocal;
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
                }
            } catch (IOException e) {
                responses.add("Error with worker: " + e.getMessage());
            }

        } else {
            // Broadcast to all workers
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
