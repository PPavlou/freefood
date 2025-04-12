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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import Reduce.Reduce;

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
     * Processes a command by mapping over local stores.
     * For commands that require reduction (client: SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME;
     * manager: LIST_STORES, DELETED_PRODUCTS), the worker sends its mapping result to the external reduce server.
     */
    public String processCommand(String command, String data) {
        List<MapReduceFramework.Pair<String, Store>> input = new ArrayList<>();

        // Determine if this command requires external reduction.
        boolean needsReduce = command.equalsIgnoreCase("SEARCH") ||
                command.equalsIgnoreCase("REVIEW") ||
                command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME") ||
                command.equalsIgnoreCase("LIST_STORES") ||
                command.equalsIgnoreCase("DELETED_PRODUCTS");

        if (command.equalsIgnoreCase("SEARCH")) {
            // Client commands: process over all local stores.
            Map<String, Store> localStores = storeManager.getAllStores();
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, localStores);
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }

            String mappingResult = gson.toJson(intermediate);
            if (needsReduce) {
                return sendToReduceServer(command, mappingResult);
            } else {
                return mappingResult;
            }

        } else if (command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")) {
            Map<String, Store> localStores = storeManager.getAllStores();
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
            // Use the new constructor to pass the aggregation query (data)
            ManagerCommandMapperReducer.CommandMapper mapper =
                    new ManagerCommandMapperReducer.CommandMapper(command, data, storeManager, productManager);
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                if (pair.getValue() != null) {
                    intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
                }
            }
            String mappingResult = gson.toJson(intermediate);
            return sendToReduceServer(command, mappingResult);
        }
        else if (command.equalsIgnoreCase("REVIEW"))
        {
            // For REVIEW, process only the target store.
            String[] parts = data.split("\\|");
            if (parts.length < 2) {
                return gson.toJson(Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Invalid data for REVIEW.")));
            }
            String storeName = parts[0].trim();
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                input.add(new MapReduceFramework.Pair<>(data, store));
            } else {
                return gson.toJson(Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
            }
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, storeManager.getAllStores());
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
            String mappingResult = gson.toJson(intermediate);
            return sendToReduceServer(command, mappingResult);
        }
        else if (command.equalsIgnoreCase("PURCHASE_PRODUCT")) {
            // For PURCHASE_PRODUCT, process only the target store.
            String[] parts = data.split("\\|");
            if (parts.length < 3) {
                return gson.toJson(Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Invalid data for PURCHASE_PRODUCT.")));
            }
            String storeName = parts[0].trim();
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                input.add(new MapReduceFramework.Pair<>(data, store));
            } else {
                return gson.toJson(Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
            }
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, storeManager.getAllStores());
            List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
            return gson.toJson(intermediate);
        } else {
            // Manager commands.
            if (command.equalsIgnoreCase("ADD_STORE")) {
                Store store = gson.fromJson(data, Store.class);
                input.add(new MapReduceFramework.Pair<>(store.getStoreName(), store));
                ManagerCommandMapperReducer.CommandMapper mapper =
                        new ManagerCommandMapperReducer.CommandMapper(command, storeManager, productManager);
                List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
                for (MapReduceFramework.Pair<String, Store> pair : input) {
                    if (pair.getValue() != null) {
                        intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
                    }
                }
                return gson.toJson(intermediate);
            } else if (command.equalsIgnoreCase("REMOVE_STORE")) {
                String storeName = data.trim();
                Store store = storeManager.getStore(storeName);
                if (store != null) {
                    input.add(new MapReduceFramework.Pair<>(storeName, store));
                } else {
                    return gson.toJson(Collections.singletonList(
                            new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
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
            } else if (command.equalsIgnoreCase("LIST_STORES") ||
                    command.equalsIgnoreCase("DELETED_PRODUCTS")) {
                // Build the mapping result only once.
                List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
                if (command.equalsIgnoreCase("LIST_STORES")) {
                    for (String storeName : storeManager.getAllStores().keySet()) {
                        intermediate.add(new MapReduceFramework.Pair<>("LIST_STORES", storeName));
                    }
                } else { // DELETED_PRODUCTS
                    intermediate.add(new MapReduceFramework.Pair<>("DELETED_PRODUCTS", productManager.getDeletedProductsReport()));
                }
                String mappingResult = gson.toJson(intermediate);
                return sendToReduceServer(command, mappingResult);
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
                    return gson.toJson(Collections.singletonList(
                            new MapReduceFramework.Pair<>("ERROR", "Store " + storeName + " not found.")));
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
            } else {
                return gson.toJson(Collections.singletonList(
                        new MapReduceFramework.Pair<>("ERROR", "Unknown command.")));
            }
        }
    }

    private String sendToReduceServer(String command, String mappingResult) {
        String reduceServerHost = "192.168.1.14";
        int reduceServerPort = Reduce.REDUCE_PORT;
        try (Socket socket = new Socket(reduceServerHost, reduceServerPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.println(command);         // send the command
            writer.println(mappingResult);     // send the mapping result as JSON
            String reducedResponse = reader.readLine();
            return reducedResponse;
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"error\":\"Error connecting to reduce server: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Establishes a connection to the master and performs the initial handshake.
     * Then it loads its assigned store partition.
     */
    public void start() {
        try {
            Socket socket = new Socket("192.168.1.14", port);
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
            // Initial load of partitioned stores.
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

                // NEW: Handle reload command from master.
                if ("RELOAD".equalsIgnoreCase(command.trim())) {
                    try {
                        int newTotalWorkers = Integer.parseInt(data.trim());
                        this.totalWorkers = newTotalWorkers;
                        // Reinitialize the store manager so that previous stores are cleared.
                        storeManager = new StoreManager();
                        loadStores();
                        writer.println("Worker " + workerId + " reloaded " + storeManager.getAllStores().size() + " stores.");
                        System.out.println("Worker " + workerId + " reloaded stores after update: " + storeManager.getAllStores().size());
                    } catch (Exception e) {
                        writer.println("Error reloading stores: " + e.getMessage());
                    }
                    continue;
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

    /**
     * Loads store JSON files from resources and partitions them based on the worker's
     * assigned workerId and the current totalWorkers count.
     */
    public void loadStores() {
        // List of JSON files (one per store) in the resources folder (e.g., /jsonf/stores/)
        String[] storeFiles = {
                "/jsonf/stores/PizzaWorld.json",
                "/jsonf/stores/CoffeeCorner.json",
                "/jsonf/stores/SouvlakiKing.json",
                "/jsonf/stores/BurgerZone.json",
                "/jsonf/stores/BakeryDelight.json",
                "/jsonf/stores/AsiaFusion.json",
                "/jsonf/stores/TacoPlace.json",
                "/jsonf/stores/SeaFoodExpress.json",
                "/jsonf/stores/VeganGarden.json",
                "/jsonf/stores/SweetTooth.json"
        };

        List<Store> allStores = new ArrayList<>();
        for (String fileName : storeFiles) {
            InputStream is = Worker.class.getResourceAsStream(fileName);
            if (is == null) {
                System.err.println("Resource not found: " + fileName);
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String jsonContent = sb.toString();
                // Each file contains a single store JSON.
                Store store = gson.fromJson(jsonContent, Store.class);
                if (store != null) {
                    allStores.add(store);
                }
            } catch (IOException e) {
                System.err.println("Error loading store from " + fileName + ": " + e.getMessage());
            }
        }

        // Partition the stores based on updated totalWorkers value.
        List<Store> partitionStores = new ArrayList<>();
        if (totalWorkers <= 0) {
            totalWorkers = 1;
        }
        for (int i = 0; i < allStores.size(); i++) {
            if (i % totalWorkers == workerId) {
                Store s = allStores.get(i);
                s.setAveragePriceOfStore();
                s.setAveragePriceOfStoreSymbol();
                partitionStores.add(s);
            }
        }
        // Add the partitioned stores to the store manager.
        for (Store s : partitionStores) {
            storeManager.addStore(s);
        }
        System.out.println("Worker " + workerId + " loaded " + partitionStores.size() + " stores out of " + allStores.size());
    }

    public static void main(String[] args) {
        Worker worker = new Worker(12345);
        worker.start();
    }
}
