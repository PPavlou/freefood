package Worker;

import com.google.gson.Gson;
import Manager.StoreManager;
import Manager.ProductManager;
import mapreduce.ClientCommandMapperReducer;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.MapReduceFramework;
import model.Store;
import model.Product;
import Reduce.Reduce;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Worker {
    private final int port;
    private StoreManager storeManager;
    private ProductManager productManager;
    private final List<Store> allStores = new ArrayList<>();

    // Worker identification (set by handshake)
    private int workerId = -1;
    private int totalWorkers = 1;

    private final Gson gson = new Gson();

    public Worker(int port) {
        this.port = port;
        this.storeManager = new StoreManager();
        this.productManager = new ProductManager();
    }

    public Worker() {
        this(12345);
    }

    /**
     * Processes a command by mapping over local stores.
     * Special-case ADD_STORE/REMOVE_STORE to mutate allStores,
     * then partition on next RELOAD.
     */
    public String processCommand(String command, String data) {
        // Dynamic ADD_STORE
        if ("ADD_STORE".equalsIgnoreCase(command)) {
            Store store = gson.fromJson(data, Store.class);
            store.setAveragePriceOfStore();
            store.setAveragePriceOfStoreSymbol();
            allStores.add(store);
            return gson.toJson(Collections.singletonList(
                    new MapReduceFramework.Pair<>(store.getStoreName(),
                            "Store " + store.getStoreName() + " added.")
            ));
        }

        // Dynamic REMOVE_STORE
        if ("REMOVE_STORE".equalsIgnoreCase(command)) {
            String storeName = data.trim();
            boolean removed = allStores.removeIf(s -> s.getStoreName().equals(storeName));
            String msg = removed
                    ? "Store " + storeName + " removed."
                    : "Store " + storeName + " not found.";
            return gson.toJson(Collections.singletonList(
                    new MapReduceFramework.Pair<>(storeName, msg)
            ));
        }

        // All other commands: delegate to original logic

        // Determine if this command requires external reduction.
        boolean needsReduce = command.equalsIgnoreCase("SEARCH") ||
                command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME") ||
                command.equalsIgnoreCase("LIST_STORES") ||
                command.equalsIgnoreCase("DELETED_PRODUCTS");

        // Build input list
        List<MapReduceFramework.Pair<String, Store>> input = new ArrayList<>();
        Map<String, Store> localStores = storeManager.getAllStores();
        if (needsReduce) {
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
        } else {
            // directed commands will pick one store
            String storeName = extractStoreName(command, data);
            Store s = storeManager.getStore(storeName);
            if (s != null) {
                input.add(new MapReduceFramework.Pair<>(data, s));
            }
        }

        List<MapReduceFramework.Pair<String, String>> intermediate = new ArrayList<>();
        if (command.equalsIgnoreCase("SEARCH") || command.equalsIgnoreCase("REVIEW")||
                command.equalsIgnoreCase("PURCHASE_PRODUCT")) {
            ClientCommandMapperReducer.ClientCommandMapper mapper =
                    new ClientCommandMapperReducer.ClientCommandMapper(command, data, localStores);
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
        } else {
            ManagerCommandMapperReducer.CommandMapper mapper =
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")
                            ? new ManagerCommandMapperReducer.CommandMapper(command, data, storeManager, productManager)
                            : new ManagerCommandMapperReducer.CommandMapper(command, storeManager, productManager);
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
        }

        String mappingJson = gson.toJson(intermediate);

        if (needsReduce) {
            return sendToReduceServer(command, mappingJson);
        } else {
            return mappingJson;
        }
    }

    private String extractStoreName(String command, String data) {
        if ("ADD_STORE".equalsIgnoreCase(command)) {
            return gson.fromJson(data, Store.class).getStoreName();
        } else if ("REMOVE_STORE".equalsIgnoreCase(command)) {
            return data.trim();
        } else {
            String[] parts = data.split("\\|", 2);
            return parts.length > 0 ? parts[0].trim() : null;
        }
    }

    private String sendToReduceServer(String command, String mappingResult) {
        int expectedCount = this.totalWorkers;
        String reduceServerHost = "192.168.1.17";
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
     * Load the *complete* set of stores from JSON into allStores.
     */
    private void loadInitialStores() {
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
            try (InputStream is = Worker.class.getResourceAsStream(fileName)) {
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
                        store.setAveragePriceOfStore();
                        store.setAveragePriceOfStoreSymbol();
                        allStores.add(store);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading store from " + fileName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Partition the current allStores list according to workerId and totalWorkers,
     * and load just that slice into our StoreManager.
     */
    private void partitionStores() {
        this.storeManager = new StoreManager();
        for (int i = 0; i < allStores.size(); i++) {
            if (i % totalWorkers == workerId) {
                Store s = allStores.get(i);
                storeManager.addStore(s);
            }
        }
        System.out.println("Worker " + workerId +
                " has " + storeManager.getAllStores().size() +
                " stores out of " + allStores.size());
    }

    public void start() {
        try (Socket socket = new Socket("192.168.1.17", port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // 1) Handshake
            writer.println("WORKER_HANDSHAKE");
            String assignMsg = reader.readLine();
            if (assignMsg != null && assignMsg.startsWith("WORKER_ASSIGN:")) {
                String[] parts = assignMsg.split(":");
                workerId = Integer.parseInt(parts[1].trim());
                totalWorkers = Integer.parseInt(parts[2].trim());
                System.out.println("Worker assigned ID " + workerId + " of " + totalWorkers);
            } else {
                throw new IOException("Invalid WORKER_ASSIGN reply: " + assignMsg);
            }

            // 2) Initial load + partition
            loadInitialStores();
            partitionStores();

            // 3) Main loop
            String command;
            while ((command = reader.readLine()) != null) {
                String data = reader.readLine();
                if ("RELOAD".equalsIgnoreCase(command.trim())) {
                    totalWorkers = Integer.parseInt(data.trim());
                    partitionStores();
                    writer.println("RELOAD_RESPONSE:" +
                            "Worker " + workerId + " reloaded " +
                            storeManager.getAllStores().size() + " stores.");
                } else {
                    String response = processCommand(command, data);
                    writer.println("CMD_RESPONSE:" + response);
                }
            }
        } catch (IOException e) {
            System.err.println("Worker " + workerId + " error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Worker worker = new Worker(12345);
        worker.start();
    }
}
