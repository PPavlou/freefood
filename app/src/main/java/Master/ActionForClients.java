package Master;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.DistributedMapReduceJob;
import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import model.Store;
import java.util.*;

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

            // For commands that are externally reduced (client: SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME; manager: LIST_STORES, DELETED_PRODUCTS),
            // merge the reduced results (each response is a JSON object representing a Map<String, String>).
            if (command.equalsIgnoreCase("SEARCH") ||
                    command.equalsIgnoreCase("REVIEW") ||
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME") ||
                    command.equalsIgnoreCase("LIST_STORES") ||
                    command.equalsIgnoreCase("DELETED_PRODUCTS")) {

                Map<String, String> combined = new HashMap<>();
                for (String response : workerResponses) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> partial = gson.fromJson(response, type);
                    for (Map.Entry<String, String> entry : partial.entrySet()) {
                        combined.merge(entry.getKey(), entry.getValue(), (v1, v2) -> v1 + "\n" + v2);
                    }
                }
                finalResponse = gson.toJson(combined);
            } else {
                // For manager commands that do not require external reduction, use the local DistributedMapReduceJob.
                ManagerCommandMapperReducer.CommandReducer reducer =
                        new ManagerCommandMapperReducer.CommandReducer(command);
                DistributedMapReduceJob<String, String, String> job =
                        new DistributedMapReduceJob<>(workerResponses, reducer);
                finalResponse = gson.toJson(job.execute());
            }
            writer.println(finalResponse);
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { }
        }
    }


    private List<String> forwardToWorkers(String command, String data) {
        List<String> responses = new ArrayList<>();

        // These commands are manager-specific and include a store name,
        // so we can direct them to a single worker using hashing.
        Set<String> directedCommands = new HashSet<>(Arrays.asList(
                "ADD_STORE", "REMOVE_STORE", "ADD_PRODUCT", "REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT", "INCREMENT_PRODUCT_AMOUNT", "DECREMENT_PRODUCT_AMOUNT",
                "PURCHASE_PRODUCT"
        ));

        // Customer commands (SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME) and
        // other broadcast manager queries (e.g., LIST_STORES, DELETED_PRODUCTS) are sent to all workers.
        if (directedCommands.contains(command.toUpperCase())) {
            // Directed command: extract store name and use hashing.
            String storeName = extractStoreName(command, data);
            if (storeName == null || storeName.isEmpty()) {
                responses.add("Error: Cannot extract store name from data for command " + command);
                return responses;
            }
            int workerCountLocal = workerSockets.size();
            if (workerCountLocal == 0) {
                responses.add("Error: No workers available.");
                return responses;
            }
            // Compute the worker index using the hash of the store name.
            int index = Math.abs(storeName.hashCode()) % workerCountLocal;
            Socket workerSocket = workerSockets.get(index);
            try {
                PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                out.println(command);
                out.println(data);
                String resp = in.readLine();
                responses.add(resp);
            } catch (IOException e) {
                responses.add("Error with worker: " + e.getMessage());
            }
        } else {
            // For customer commands and other manager queries that require checking every partition,
            // broadcast the request to all workers.
            synchronized (workerSockets) {
                for (Socket workerSocket : workerSockets) {
                    try {
                        PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                        out.println(command);
                        out.println(data);
                        String resp = in.readLine();
                        responses.add(resp);
                    } catch (IOException e) {
                        responses.add("Error with worker: " + e.getMessage());
                    }
                }
            }
        }
        return responses;
    }

    /**
     * Helper method to extract the store name from the command data.
     * For ADD_STORE, data is expected to be a JSON string representing a Store.
     * For REMOVE_STORE, data is simply the store name.
     * For product-related commands, data is in the format "storeName|otherParameters".
     */
    private String extractStoreName(String command, String data) {
        if (command.equalsIgnoreCase("ADD_STORE")) {
            try {
                Store store = new Gson().fromJson(data, Store.class);
                return store.getStoreName();
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
            // Expected data format: "storeName|otherData"
            String[] parts = data.split("\\|", 2);
            if (parts.length >= 1) {
                return parts[0].trim();
            } else {
                return null;
            }
        }
        // For broadcast commands (customer filters), no store name is extracted.
        return null;
    }

}
