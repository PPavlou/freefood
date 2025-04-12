package Master;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
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
            String finalResponse = "";

            // For reduce commands—REVIEW has been removed so it is handled via direct response.
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
            writer.println(finalResponse);
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { }
        }
    }

    /**
     * Forwards the client/manager request to worker(s).
     * For directed commands (including REVIEW), a specific worker is chosen.
     * For broadcast commands, the request is sent to all workers.
     * This method reads responses and only uses those starting with "CMD_RESPONSE:".
     */
    private List<String> forwardToWorkers(String command, String data) {
        List<String> responses = new ArrayList<>();

        // Include REVIEW as a directed command.
        Set<String> directedCommands = new HashSet<>(Arrays.asList(
                "ADD_STORE", "REMOVE_STORE", "ADD_PRODUCT", "REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT", "INCREMENT_PRODUCT_AMOUNT", "DECREMENT_PRODUCT_AMOUNT",
                "PURCHASE_PRODUCT", "REVIEW"
        ));

        if (directedCommands.contains(command.toUpperCase())) {
            String storeName = extractStoreName(command, data);
            if (storeName == null || storeName.isEmpty()) {
                responses.add("Error: Cannot extract store name from data for command " + command);
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
                    BufferedReader in = new BufferedReader(new java.io.InputStreamReader(workerSocket.getInputStream()));
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
            }
            for (Socket workerSocket : workerSockets) {
                try {
                    synchronized (workerSocket) {
                        PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new java.io.InputStreamReader(workerSocket.getInputStream()));
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
     * Helper method to extract the store name from command data.
     */
    private String extractStoreName(String command, String data) {
        if (command.equalsIgnoreCase("ADD_STORE")) {
            try {
                return new com.google.gson.Gson().fromJson(data, model.Store.class).getStoreName();
            } catch (Exception e) {
                return null;
            }
        } else if (command.equalsIgnoreCase("REMOVE_STORE")) {
            return data.trim();
        } else if (command.equalsIgnoreCase("REVIEW")) {
            // For REVIEW the data is expected in "StoreName|..." format.
            String[] parts = data.split("\\|");
            return parts.length >= 1 ? parts[0].trim() : null;
        } else if (command.equalsIgnoreCase("ADD_PRODUCT") ||
                command.equalsIgnoreCase("REMOVE_PRODUCT") ||
                command.equalsIgnoreCase("UPDATE_PRODUCT_AMOUNT") ||
                command.equalsIgnoreCase("INCREMENT_PRODUCT_AMOUNT") ||
                command.equalsIgnoreCase("DECREMENT_PRODUCT_AMOUNT") ||
                command.equalsIgnoreCase("PURCHASE_PRODUCT")) {
            String[] parts = data.split("\\|", 2);
            if (parts.length >= 1) {
                return parts[0].trim();
            } else {
                return null;
            }
        }
        return null;
    }
}
