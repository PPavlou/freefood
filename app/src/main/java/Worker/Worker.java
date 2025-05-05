package Worker;

import Master.MasterServer;
import com.google.gson.Gson;
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

/**
 * Worker node for the Freefooders system.
 * Connects to the Master server, loads and partitions store data,
 * processes client and manager commands by mapping over local stores,
 * and sends mapping results to the external reduce server when required.
 */
public class Worker {
    private int port;
    private StoreManager storeManager;
    private ProductManager productManager;
    private final List<Store> allStores = new ArrayList<>();  // full store list for dynamic add/remove
    private int workerId = -1;    // assigned by handshake
    private int totalWorkers = 1;
    private Gson gson = new Gson();
    private PrintWriter writer;
    private Socket socket;

    /**
     * Constructs a Worker that connects to the Master on the specified port.
     *
     * @param port the port to connect to the Master server
     */
    public Worker(int port) {
        this.port = port;
        storeManager = new StoreManager();
        productManager = new ProductManager();
    }

    /**
     * Constructs a Worker that connects to the Master on the default port 12345.
     */
    public Worker() {
        this(12345);
    }

    /**
     * Determines which worker should handle a specific store based on consistent hashing.
     *
     * @param storeName the name of the store
     * @param totalWorkers the total number of workers
     * @return the worker ID that should handle this store
     */
    public static int getWorkerIdForStore(String storeName, int totalWorkers) {
        if (totalWorkers <= 0) {
            return 0;
        }
        return Math.abs(storeName.hashCode()) % totalWorkers;
    }

    /**
     * Checks if this worker should handle the specified store.
     *
     * @param storeName the name of the store
     * @return true if this worker should handle the store, false otherwise
     */
    public boolean shouldHandleStore(String storeName) {
        return getWorkerIdForStore(storeName, totalWorkers) == workerId;
    }

    /**
     * Processes a command by mapping over local stores.
     * For commands requiring reduction (SEARCH, AGGREGATE_SALES_BY_PRODUCT_NAME,
     * LIST_STORES, DELETED_PRODUCTS), sends mapping results to the external reduce server.
     *
     * @param command the command to process
     * @param data    the associated data payload
     * @return a JSON string representing the mapping result or status message
     */
    public String processCommand(String command, String data) {
        // dynamic add/remove updates the full list, then await reload
        if (command.equalsIgnoreCase("ADD_STORE")) {
            Store store = gson.fromJson(data, Store.class);
            store.setAveragePriceOfStore();
            store.setAveragePriceOfStoreSymbol();
            allStores.add(store);
            return gson.toJson(Collections.singletonList(
                    new MapReduceFramework.Pair<>(store.getStoreName(),
                            "Store " + store.getStoreName() + " added.")
            ));
        }
        if (command.equalsIgnoreCase("REMOVE_STORE")) {
            String storeName = data.trim();
            boolean removed = allStores.removeIf(s -> s.getStoreName().equals(storeName));
            String msg = removed
                    ? "Store " + storeName + " removed."
                    : "Store " + storeName + " not found.";
            return gson.toJson(Collections.singletonList(
                    new MapReduceFramework.Pair<>(storeName, msg)
            ));
        }

        List<MapReduceFramework.Pair<String, Store>> input = new ArrayList<>();

        // Determine if this command requires external reduction.
        boolean needsReduce = command.equalsIgnoreCase("SEARCH") ||
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
        }
        else if (command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")) {
            Map<String, Store> localStores = storeManager.getAllStores();
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
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
        else if (command.equalsIgnoreCase("REVIEW")) {
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
            // For REVIEW, do not reduce; return the mapping result directly.
            return gson.toJson(intermediate);
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
                List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();

                if (command.equalsIgnoreCase("LIST_STORES")) {
                    for (String storeName : storeManager.getAllStores().keySet()) {
                        intermediate.add(new MapReduceFramework.Pair<>("LIST_STORES", storeName));
                    }
                } else { // DELETED_PRODUCTS
                    // only send a mapping if there are actual deletions
                    String report = productManager.getDeletedProductsReport();
                    if (report != null
                            && !report.trim().isEmpty()
                            && !report.startsWith("No products have been deleted")) {
                        intermediate.add(new MapReduceFramework.Pair<>("DELETED_PRODUCTS", report));
                    }
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

    /**
     * Sends mapping results to the reduce server for commands that require reduction.
     *
     * @param command        the command name
     * @param mappingResult  JSON string of mapping output
     * @return status message as JSON
     */
    private String sendToReduceServer(String command, String mappingResult) {
        int expectedCount = this.totalWorkers;
        String reduceServerHost = "localhost";
        int reduceServerPort = Reduce.REDUCE_PORT;
        try (Socket socket = new Socket(reduceServerHost, reduceServerPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            writer.println(command);
            writer.println(String.valueOf(expectedCount));
            writer.println(mappingResult);
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"error\":\"Error connecting to reduce server: " + e.getMessage() + "\"}";
        }
        return "{\"status\":\"Mapping output sent to reduce server.\"}";
    }

    /**
     * Loads store JSON resources and partitions them based on workerId and totalWorkers.
     * Initializes the StoreManager with the partition assigned to this worker.
     */
    public void loadStores() {
        // initial JSON load
        if (allStores.isEmpty()) {
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
                    Store store = gson.fromJson(sb.toString(), Store.class);
                    if (store != null) {
                        allStores.add(store);
                    }
                } catch (IOException e) {
                    System.err.println("Error loading store from " + fileName + ": " + e.getMessage());
                }
            }
        }

        // partition from full list using hash-based mapping
        List<Store> partitionStores = new ArrayList<>();
        if (totalWorkers <= 0) {
            totalWorkers = 1;
        }
        for (Store s : allStores) {
            // Use the consistent hashing method
            if (shouldHandleStore(s.getStoreName())) {
                s.setAveragePriceOfStore();
                s.setAveragePriceOfStoreSymbol();
                partitionStores.add(s);
            }
        }

        storeManager = new StoreManager();
        for (Store s : partitionStores) {
            storeManager.addStore(s);
        }

        System.out.println("Worker " + workerId + " loaded " +
                partitionStores.size() + " stores out of " +
                allStores.size());
    }

    /**
     * Starts the worker:
     * performs handshake with the Master server, loads stores,
     * listens for commands to process, and handles reload and shutdown events.
     */
    public void start() {
        try {
            socket = new Socket("localhost", port);
            writer = new PrintWriter(socket.getOutputStream(), true);
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
            // Load the worker's store partition.
            loadStores();
            System.out.println("Worker " + workerId + " connected to master on port " + port);

            checkKeyboardInputForShutdown();

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

                // Handle reload commands.
                if ("RELOAD".equalsIgnoreCase(command.trim())) {
                    try {
                        int newTotalWorkers = Integer.parseInt(data.trim());
                        this.totalWorkers = newTotalWorkers;
                        System.out.println("Total Workers: " + totalWorkers);
                        loadStores();
                        writer.println("RELOAD_RESPONSE:" + "Worker " + workerId + " reloaded " + storeManager.getAllStores().size() + " stores.");
                        System.out.println("Worker " + workerId + " reloaded stores after update: " + storeManager.getAllStores().size());
                    } catch (Exception e) {
                        writer.println("RELOAD_RESPONSE:" + "Error reloading stores: " + e.getMessage());
                    }
                    continue;
                } else if ("DECREMENT_ID".equalsIgnoreCase(command.trim())) {
                    String[] parts = data.split(":");
                    int newId    = Integer.parseInt(parts[0].trim());
                    int newTotal = Integer.parseInt(parts[1].trim());

                    this.workerId     = newId;
                    this.totalWorkers = newTotal;
                    System.out.println("Worker changed id to: " + workerId);
                    continue;
                }
                System.out.println("Worker " + workerId + " received command: " + command);
                System.out.println("Worker " + workerId + " received data: " + data);

                String response = processCommand(command, data);
                writer.println("CMD_RESPONSE:" + response);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Worker " + workerId + " error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a shutdown notification to the Master server
     * so that this worker can be gracefully deregistered.
     *
     * @throws IOException if an I/O error occurs when connecting
     */
    private void sendTerminationCommand() throws IOException {
        try {
            try (Socket socketTemp = new Socket("localhost", port);
                 PrintWriter writerTemp = new PrintWriter(socketTemp.getOutputStream(), true)) { // Auto-flush enabled
                writerTemp.println("WORKER_SHUTDOWN:" + workerId);
                totalWorkers--;
                System.out.println("Shutdown notification sent on new connection.");
            }
        } catch (IOException e) {
            System.err.println("Failed to send shutdown notification: " + e.getMessage());
        }
    }

    /**
     * Starts a background thread that listens for "SHUTDOWN" on the console input
     * and exits the process when received.
     */
    private void checkKeyboardInputForShutdown() {
        new Thread(() -> {
            BufferedReader terminalReader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                try {
                    String cmd = terminalReader.readLine();
                    if ("SHUTDOWN".equalsIgnoreCase(cmd.trim())) {
                        System.out.println("Manual shutdown command received.");
                        System.exit(0);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading from terminal: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Main entry point for the Worker application.
     * Registers a shutdown hook to notify Master before exit and starts the worker.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        Worker worker = new Worker(12345);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered for Worker");
            try {
                worker.sendTerminationCommand();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        worker.start();
    }
}