package Reduce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.ClientCommandMapperReducer;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.MapReduceFramework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ReduceHandler implements Runnable {
    private Socket socket;

    public ReduceHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // Read the command and mapping result from the worker.
            String command = reader.readLine();
            String mappingJson = reader.readLine();
            System.out.println("Reduce server received command: " + command);
            System.out.println("Mapping result JSON: " + mappingJson);

            Gson gson = new Gson();
            Type pairListType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>(){}.getType();
            List<MapReduceFramework.Pair<String, String>> pairs = gson.fromJson(mappingJson, pairListType);

            // Group by key.
            Map<String, List<String>> grouped = new HashMap<>();
            for (MapReduceFramework.Pair<String, String> pair : pairs) {
                grouped.computeIfAbsent(pair.getKey(), k -> new ArrayList<>()).add(pair.getValue());
            }

            // Choose the proper reducer based on command.
            Map<String, String> reducedResults = new HashMap<>();
            if (command.equalsIgnoreCase("SEARCH") ||
                    command.equalsIgnoreCase("REVIEW") ||
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")) {

                // Use client reducer.
                ClientCommandMapperReducer.ClientCommandReducer reducer =
                        new ClientCommandMapperReducer.ClientCommandReducer(command);
                for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                    String reduced = reducer.reduce(entry.getKey(), entry.getValue());
                    reducedResults.put(entry.getKey(), reduced);
                }
            } else if (command.equalsIgnoreCase("LIST_STORES") ||
                    command.equalsIgnoreCase("DELETED_PRODUCTS")) {
                // Use manager reducer.
                ManagerCommandMapperReducer.CommandReducer reducer =
                        new ManagerCommandMapperReducer.CommandReducer(command);
                for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                    String reduced = reducer.reduce(entry.getKey(), entry.getValue());
                    reducedResults.put(entry.getKey(), reduced);
                }
            } else {
                // For commands that do not need external reducing, simply return the raw mapping.
                reducedResults.put("result", mappingJson);
            }

            String resultJson = gson.toJson(reducedResults);
            writer.println(resultJson);
            System.out.println("Reduced result sent: " + resultJson);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { }
        }
    }
}
