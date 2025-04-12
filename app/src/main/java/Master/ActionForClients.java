package Master;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

            String finalResponse;
            Gson gson = new Gson();

            // If the message is coming from the reduce server...
            if ("REDUCE_RESULT".equalsIgnoreCase(command)) {
                // Directly use the reduced result as final response
                finalResponse = data;
            } else {
                String workerResponses = gson.toJson(forwardToWorkers(command, data));
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
     * Forwards the client/manager request to worker(s). If the command
     * is directed (e.g., product modification commands), it selects a specific worker.
     * If it's a broadcast command (e.g., SEARCH), it sends the request to all workers.
     * Uses the dedicated monitor (MasterServer.workerAvailable) for waiting.
     */
    private List<String> forwardToWorkers(String command, String data) {
        List<String> responses = new ArrayList<>();

        // Set of commands that are directed to one specific worker.
        Set<String> directedCommands = new HashSet<>(Arrays.asList(
                "ADD_STORE", "REMOVE_STORE", "ADD_PRODUCT", "REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT", "INCREMENT_PRODUCT_AMOUNT", "DECREMENT_PRODUCT_AMOUNT",
                "PURCHASE_PRODUCT"
        ));

        if (directedCommands.contains(command.toUpperCase())) {
            String storeName = extractStoreName(command, data);
            if (storeName == null || storeName.isEmpty()) {
                responses.add("Error: Cannot extract store name from data for command " + command);
                return responses;
            }
            Socket workerSocket;
            // Wait for at least one worker using the dedicated monitor.
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
                // Synchronize on the worker socket to ensure exclusive access.
                synchronized (workerSocket) {
                    PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                    out.println(command);
                    out.println(data);
                    String resp = in.readLine();
                    responses.add(resp);
                }
            } catch (IOException e) {
                responses.add("Error with worker: " + e.getMessage());
            }
        } else {
            // For broadcast commands: wait for at least one worker and then broadcast.
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
                // Use each worker in turn.
                for (Socket workerSocket : workerSockets) {
                    try {
                        synchronized (workerSocket) {
                            PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                            out.println(command);
                            out.println(data);
                            String resp = in.readLine();
                            responses.add(resp);
                        }
                    } catch (IOException e) {
                        responses.add("Error with worker: " + e.getMessage());
                    }
                }
            }
        }
        return responses;
    }

    /**
     * Helper method to extract the store name from command data.
     * For ADD_STORE, data is expected to be a JSON string representing a Store.
     * For REMOVE_STORE, data is simply the store name.
     * For product-related commands, data is in the format "storeName|otherParameters".
     */
    private String extractStoreName(String command, String data) {
        if (command.equalsIgnoreCase("ADD_STORE")) {
            try {
                return new Gson().fromJson(data, model.Store.class).getStoreName();
            } catch (Exception e) {
                return null;
            }
        } else if (command.equalsIgnoreCase("REMOVE_STORE")) {
            return data.trim();
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
        // Broadcast commands have no specific store.
        return null;
    }
}
