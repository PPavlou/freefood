package Worker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import Manager.StoreManager;
import Manager.ProductManager;
import mapreduce.ClientCommandMapperReducer;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.MapReduceFramework;
import model.Store;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Worker {
    private int port;
    private StoreManager storeManager;
    private ProductManager productManager;
    // Worker identification (set by handshake)
    private int workerId = -1;
    private int totalWorkers = 1;
    private Gson gson = new Gson();

    public Worker(int port) {
        this.port = port;
        storeManager = new StoreManager();
        productManager = new ProductManager();
    }

    public Worker() {
        this(12345);
    }

    /**
     * Processes a command by building mapper input from local stores and invoking the appropriate mapper.
     * Client commands: SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME.
     * Manager commands: ADD_STORE, REMOVE_STORE, ADD_PRODUCT, REMOVE_PRODUCT, UPDATE_PRODUCT_AMOUNT,
     *                   INCREMENT_PRODUCT_AMOUNT, DECREMENT_PRODUCT_AMOUNT, DELETED_PRODUCTS, LIST_STORES.
     * Always returns a JSON array.
     */
    public String processCommand(String command, String data) {
        List<MapReduceFramework.Pair<String, Store>> input = new ArrayList<>();

        // Check for client commands.
        if (command.equalsIgnoreCase("SEARCH") ||
                command.equalsIgnoreCase("REVIEW") ||
                command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")) {

            // For these client commands, process over all local stores.
            Map<String, Store> localStores = storeManager.getAllStores();
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
            // Instantiate the client mapper.
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, localStores);
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
            return gson.toJson(intermediate);

        } else if (command.equalsIgnoreCase("PURCHASE_PRODUCT")) {
            // For PURCHASE_PRODUCT, process only the target store.
            // Expected data format: "storeName|productName|quantity"
            String[] parts = data.split("\\|");
            if (parts.length < 3) {
                return gson.toJson(java.util.Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Invalid data for PURCHASE_PRODUCT.")));
            }
            String storeName = parts[0].trim();
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                // Build input for just this store.
                input.add(new MapReduceFramework.Pair<>(data, store));
            } else {
                return gson.toJson(java.util.Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
            }
            // Use the client mapper (which includes a PURCHASE_PRODUCT case)
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, storeManager.getAllStores());
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            // For purchase, only one store is processed.
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
            return gson.toJson(intermediate);

        } else {
            // Manager commands.
            if (command.equalsIgnoreCase("ADD_STORE")) {
                Store store = gson.fromJson(data, Store.class);
                input.add(new MapReduceFramework.Pair<>(store.getStoreName(), store));
            } else if (command.equalsIgnoreCase("REMOVE_STORE")) {
                String storeName = data.trim();
                Store store = storeManager.getStore(storeName);
                if (store != null) {
                    input.add(new MapReduceFramework.Pair<>(storeName, store));
                } else {
                    return gson.toJson(java.util.Collections.singletonList(
                            new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
                }
            } else if (command.equalsIgnoreCase("DELETED_PRODUCTS") ||
                    command.equalsIgnoreCase("LIST_STORES")) {
                for (Store s : storeManager.getAllStores().values()) {
                    input.add(new MapReduceFramework.Pair<>(command, s));
                }
            } else if (command.equalsIgnoreCase("ADD_PRODUCT") ||
                    command.equalsIgnoreCase("REMOVE_PRODUCT") ||
                    command.equalsIgnoreCase("UPDATE_PRODUCT_AMOUNT") ||
                    command.equalsIgnoreCase("INCREMENT_PRODUCT_AMOUNT") ||
                    command.equalsIgnoreCase("DECREMENT_PRODUCT_AMOUNT")) {
                String storeName = data.split("\\|")[0].trim();
                Store store = storeManager.getStore(storeName);
                if (store != null) {
                    input.add(new MapReduceFramework.Pair<>(data, store));
                } else {
                    return gson.toJson(java.util.Collections.singletonList(
                            new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
                }
            } else {
                return gson.toJson(java.util.Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Unknown command.")));
            }

            ManagerCommandMapperReducer.CommandMapper mapper =
                    new ManagerCommandMapperReducer.CommandMapper(command, storeManager, productManager);
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                if (pair.getValue() != null) {
                    intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
                }
            }
            return gson.toJson(intermediate);
        }
    }




    public void start() {
        try {
            Socket socket = new Socket("localhost", port);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send handshake.
            writer.println("WORKER_HANDSHAKE");
            String assignMsg = reader.readLine();
            if (assignMsg != null && assignMsg.startsWith("WORKER_ASSIGN:")) {
                String[] parts = assignMsg.split(":");
                if (parts.length == 3) {
                    try {
                        workerId = Integer.parseInt(parts[1].trim());
                        totalWorkers = Integer.parseInt(parts[2].trim());
                        System.out.println("Worker assigned ID " + workerId + " out of " + totalWorkers);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid worker assignment format.");
                    }
                }
            } else {
                System.err.println("Did not receive proper assignment from Master.");
            }
            // Load partitioned stores.
            loadStores();
            System.out.println("Worker " + workerId + " connected to master on port " + port);
            while (true) {
                String command = reader.readLine();
                if (command == null) {
                    System.out.println("Master closed connection.");
                    break;
                }
                String data = reader.readLine();
                if (data == null) {
                    System.out.println("Master closed connection.");
                    break;
                }
                System.out.println("Worker " + workerId + " received command: " + command);
                System.out.println("Worker " + workerId + " received data: " + data);

                String response = processCommand(command, data);
                writer.println(response);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Worker " + workerId + " error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadStores() {
        InputStream is = Worker.class.getResourceAsStream("/jsonf/Stores.json");
        if (is == null) {
            System.err.println("Resource not found: /jsonf/Stores.json");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String jsonContent = sb.toString();
            List<Store> allStores = gson.fromJson(jsonContent, new TypeToken<List<Store>>(){}.getType());
            List<Store> partitionStores = new ArrayList<>();
            if (totalWorkers <= 0) {
                totalWorkers = 1;
            }
            // Partition the list: assign each store based on its index modulo totalWorkers.
            for (int i = 0; i < allStores.size(); i++) {
                if (i % totalWorkers == workerId) {
                    Store s = allStores.get(i);
                    s.setAveragePriceOfStore();
                    s.setAveragePriceOfStoreSymbol();
                    partitionStores.add(s);
                }
            }
            // Add only the partitioned stores to the local StoreManager.
            for (Store s : partitionStores) {
                storeManager.addStore(s);
            }
            System.out.println("Worker " + workerId + " loaded " + partitionStores.size() + " stores out of " + allStores.size());
        } catch (IOException e) {
            System.err.println("Error loading stores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Worker worker = new Worker(12345);
        worker.start();
    }
}
