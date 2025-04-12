package Master;

import Reduce.Reduce;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

import mapreduce.MapReduceFramework;
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
            String finalResponse;
            Gson gson = new Gson();

            // For commands that are externally reduced (client: SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME;
            // manager: LIST_STORES, DELETED_PRODUCTS), merge the reduced results.
            if (command.equalsIgnoreCase("SEARCH") ||
                    command.equalsIgnoreCase("REVIEW") ||
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME") ||
                    command.equalsIgnoreCase("LIST_STORES") ||
                    command.equalsIgnoreCase("DELETED_PRODUCTS")) {

                // Parse and aggregate the mapping results from all workers.
                List<MapReduceFramework.Pair<String, String>> combinedPairs = new ArrayList<>();
                Type type = new TypeToken<List<MapReduceFramework.Pair<String, String>>>() {}.getType();
                for (String mappingJson : workerResponses) {
                    List<MapReduceFramework.Pair<String, String>> pairs = gson.fromJson(mappingJson, type);
                    combinedPairs.addAll(pairs);
                }
                String aggregatedMappingResult = gson.toJson(combinedPairs);

                finalResponse = sendToReduceServer(command, aggregatedMappingResult);
            } else {
                // For other commands, simply concatenate the responses.
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
     * Sends the mapping result to the Reduce server and waits for the reduced result.
     * The Reduce server is expected to be running on a specific host and port.
     */
    private String sendToReduceServer(String command, String mappingResult) {
        // Since the mapping results are already combined, we expect only one reduce input.
        int expectedCount = 1;
        String reduceServerHost = "192.168.1.14"; // Adjust as needed.
        int reduceServerPort = Reduce.REDUCE_PORT;
        try (Socket socket = new Socket(reduceServerHost, reduceServerPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.println(command);
            writer.println(expectedCount);
            writer.println(mappingResult);
            String finalReducedResult = reader.readLine();
            return finalReducedResult;
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"error\":\"Error connecting to reduce server: " + e.getMessage() + "\"}";
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