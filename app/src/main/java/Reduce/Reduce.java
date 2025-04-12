package Reduce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.ClientCommandMapperReducer;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.MapReduceFramework;
import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Reduce {
    public static final int REDUCE_PORT = 23456;
    // Set the expected number of worker mapping outputs per reduction job.
    public static final int EXPECTED_WORKER_COUNT = 2; // Adjust as needed.

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(REDUCE_PORT)) {
            System.out.println("Reduce server listening on port " + REDUCE_PORT);

            // Main loop: for each new reduction job, collect the mappings from all workers.
            while (true) {
                List<Socket> sockets = new ArrayList<>();
                List<String> mappingResults = new ArrayList<>();
                String command = null;

                // Loop to collect mapping outputs from all workers assigned to this command.
                for (int i = 0; i < EXPECTED_WORKER_COUNT; i++) {
                    Socket socket = serverSocket.accept();
                    sockets.add(socket);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // For the first connection, record the command; for subsequent ones, compare.
                    if (i == 0) {
                        command = reader.readLine();
                    } else {
                        String cmd = reader.readLine();
                        if (command != null && !command.equalsIgnoreCase(cmd)) {
                            System.err.println("Warning: Mismatched command received. Expected "
                                    + command + " but got " + cmd);
                        }
                    }
                    String mappingJson = reader.readLine();
                    mappingResults.add(mappingJson);
                }

                // Combine all mapping outputs.
                Gson gson = new Gson();
                Type pairListType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>() {}.getType();
                List<MapReduceFramework.Pair<String, String>> combinedPairs = new ArrayList<>();
                for (String mappingJson : mappingResults) {
                    mappingJson = mappingJson.trim();
                    List<MapReduceFramework.Pair<String, String>> pairs;
                    try {
                        if (mappingJson.startsWith("{")) {
                            // If the JSON is an object, parse it as Map<String, String> and
                            // then convert each entry into a Pair.
                            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                            Map<String, String> resultMap = gson.fromJson(mappingJson, mapType);
                            pairs = new ArrayList<>();
                            for (Map.Entry<String, String> entry : resultMap.entrySet()) {
                                pairs.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
                            }
                        } else if (mappingJson.startsWith("[")) {
                            // Expected case: mappingJson is an array of Pair objects.
                            pairs = gson.fromJson(mappingJson, pairListType);
                        } else {
                            System.err.println("Unexpected mapping result format: " + mappingJson);
                            pairs = new ArrayList<>();
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing mapping result: " + e.getMessage());
                        pairs = new ArrayList<>();
                    }
                    combinedPairs.addAll(pairs);
                }

                // Group values by key.
                Map<String, List<String>> grouped = new HashMap<>();
                for (MapReduceFramework.Pair<String, String> pair : combinedPairs) {
                    grouped.computeIfAbsent(pair.getKey(), k -> new ArrayList<>()).add(pair.getValue());
                }

                // Apply the proper reducer based on the command.
                Map<String, String> reducedResults = new HashMap<>();
                if (command != null && (command.equalsIgnoreCase("SEARCH")
                        || command.equalsIgnoreCase("REVIEW")
                        || command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME"))) {

                    ClientCommandMapperReducer.ClientCommandReducer reducer =
                            new ClientCommandMapperReducer.ClientCommandReducer(command);
                    for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                        String reduced = reducer.reduce(entry.getKey(), entry.getValue());
                        reducedResults.put(entry.getKey(), reduced);
                    }
                } else if (command != null && (command.equalsIgnoreCase("LIST_STORES")
                        || command.equalsIgnoreCase("DELETED_PRODUCTS"))) {

                    ManagerCommandMapperReducer.CommandReducer reducer =
                            new ManagerCommandMapperReducer.CommandReducer(command);
                    for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                        String reduced = reducer.reduce(entry.getKey(), entry.getValue());
                        reducedResults.put(entry.getKey(), reduced);
                    }
                } else {
                    // For commands that do not need external reducing, simply return the raw mapping.
                    reducedResults.put("result", combinedPairs.toString());
                }

                String resultJson = gson.toJson(reducedResults);
                System.out.println("Final reduced result: " + resultJson);

                try (Socket masterSocket = new Socket("localhost", 12345)) {  // adjust "localhost" if Master Server is remote
                    PrintWriter masterOut = new PrintWriter(masterSocket.getOutputStream(), true);
                    // Send an identifier so the master knows this is a reduced output
                    masterOut.println("REDUCE_RESULT");
                    // Then send the actual reduced result (in JSON)
                    masterOut.println(resultJson);
                    System.out.println("Reduced result sent to Master Server.");
                } catch (IOException e) {
                    System.err.println("Error sending reduced result to Master Server: " + e.getMessage());
                }

                // Optionally, close the worker sockets as they are no longer needed.
                for (Socket s : sockets) {
                    try {
                        s.close();
                    } catch (IOException e) {
                        // Log or handle the error if necessary
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Reduce server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
