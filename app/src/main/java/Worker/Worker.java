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
import java.util.*;
import java.net.ServerSocket;
import java.io.IOException;
import Reduce.Reduce;

/**
 * Worker node for the Freefooders system.
 * Connects to the Master server (optionally), loads and partitions store data,
 * processes client and manager commands by mapping over local stores,
 * and sends mapping results to the external reduce server when required.
 */
public class Worker {
    private final String masterHost = "172.20.10.3";  // Master address, optional
    private final int masterPort;
    private final int commandPort;
    private StoreManager storeManager;
    private ProductManager productManager;
    private final List<Store> allStores = new ArrayList<>();
    private int workerId = 0;    // defaults for standalone
    private int totalWorkers = 1;
    private final Gson gson = new Gson();

    public Worker(int masterPort, int commandPort) {
        this.masterPort = masterPort;
        this.commandPort = commandPort;
    }

    public Worker() {
        this(12345, 20000);
    }

    public static int getWorkerIdForStore(String storeName, int totalWorkers) {
        if (totalWorkers <= 0) return 0;
        return Math.abs(storeName.hashCode()) % totalWorkers;
    }

    public boolean shouldHandleStore(String storeName) {
        return getWorkerIdForStore(storeName, totalWorkers) == workerId;
    }

    public String processCommand(String command, String data, String jobId) {
        // dynamic add/remove updates the full list
        if (command.contains("ADD_STORE")) {
            Store store = gson.fromJson(data, Store.class);
            store.setAveragePriceOfStore();
            store.setAveragePriceOfStoreSymbol();
            allStores.add(store);
            return gson.toJson(Collections.singletonList(
                    new MapReduceFramework.Pair<>(store.getStoreName(),
                            "Store " + store.getStoreName() + " added.")
            ));
        }
        if (command.contains("REMOVE_STORE")) {
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
                return sendToReduceServer(command, mappingResult, jobId);
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
            return sendToReduceServer(command, mappingResult, jobId);
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
            if (command.contains("ADD_STORE")) {
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
            } else if (command.contains("REMOVE_STORE")) {
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
                return sendToReduceServer(command, mappingResult, jobId);
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
     * @param jobId          the unique job ID for this operation
     * @return status message as JSON
     */
    private String sendToReduceServer(String command, String mappingJson, String jobId) {
        int expectedCount = this.totalWorkers;
        String reduceHost = "172.20.10.3";
        int reducePort = Reduce.REDUCE_PORT;
        try (Socket socket = new Socket(reduceHost, reducePort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(jobId);
            out.println(command);
            out.println(expectedCount);
            out.println(mappingJson);
        } catch (IOException e) {
            return "{\"error\":\"Error connecting to reduce server: "
                    + e.getMessage() + "\"}";
        }
        return "{\"status\":\"Mapping output sent\"}";
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
        // 1) Open your command socket so broadcastReload can connect even during handshake
        ServerSocket server;
        try {
            server = new ServerSocket(commandPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind on port " + commandPort, e);
        }

        // 2) Handshake + collect dynamic‐replay messages
        List<Store> replayAdds    = new ArrayList<>();
        List<String> replayRemoves = new ArrayList<>();
        try (Socket reg = new Socket(masterHost, masterPort);
             PrintWriter out = new PrintWriter(reg.getOutputStream(), true);
             BufferedReader in  = new BufferedReader(
                     new InputStreamReader(reg.getInputStream()))) {

            // tell master who we are
            out.println("WORKER_HANDSHAKE");
            out.println(commandPort);

            // first line is the assignment
            String assign = in.readLine();
            if (assign != null && assign.startsWith("WORKER_ASSIGN:")) {
                String[] parts = assign.split(":");
                workerId     = Integer.parseInt(parts[1].trim());
                totalWorkers = Integer.parseInt(parts[2].trim());
                System.out.println("Worker assigned ID " + workerId + " of " + totalWorkers);
            } else {
                throw new IllegalStateException("Bad handshake response: " + assign);
            }

            // now read *all* the replay commands until master closes the socket
            String line;
            while ((line = in.readLine()) != null) {
                switch (line) {
                    case "ADD_STORE(REPLAY)" -> {
                        String json  = in.readLine();
                        in.readLine();           // jobId (we can ignore it here)
                        Store s = gson.fromJson(json, Store.class);
                        replayAdds.add(s);
                    }
                    case "REMOVE_STORE(REPLAY)" -> {
                        String name = in.readLine();
                        in.readLine();           // jobId
                        replayRemoves.add(name.trim());
                    }
                    default -> {
                        // ignore any other lines
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: cannot reach Master for handshake, running standalone: " + e.getMessage());
            workerId     = 0;
            totalWorkers = 1;
        }

        // 3) Load your static JSON stores exactly once
        loadStores();  // this populates `allStores` and partitions into storeManager

        // 4) Apply the replays you collected
        for (Store s : replayAdds) {
            s.setAveragePriceOfStore();
            s.setAveragePriceOfStoreSymbol();
            allStores.add(s);
        }
        for (String name : replayRemoves) {
            allStores.removeIf(s -> s.getStoreName().equals(name));
        }

        // 5) Re-partition so storeManager reflects both static + dynamic stores
        loadStores();

        System.out.println("Worker " + workerId + " loaded stores (incl. dynamic) and ready; listening on port " + commandPort);

        // 6) Shutdown hook and begin serving commands
        checkKeyboardInputForShutdown();
        // 5) accept-loop
        while (true) {
            try (Socket s = server.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(s.getInputStream()));
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                String command = in.readLine();
                String data    = in.readLine();
                String jobId   = in.readLine();
                if (command == null) continue;

                System.out.printf("Worker %d received %s / %s / %s%n",
                        workerId, command, data, jobId);

                if ("RELOAD".equalsIgnoreCase(command)) {
                    totalWorkers = Integer.parseInt(data.trim());
                    loadStores();
                    out.println("RELOAD_RESPONSE: reloaded");
                    continue;
                }
                if ("DECREMENT_ID".equalsIgnoreCase(command)) {
                    String[] p = data.split(":");
                    workerId = Integer.parseInt(p[0]);
                    totalWorkers = Integer.parseInt(p[1]);
                    continue;
                }

                String response;
                if (command.contains("|")) {
                    String[] parts = command.split("\\|",3);
                    response = processCommand(parts[0], parts[1], parts[2]);
                } else {
                    response = processCommand(command, data, jobId);
                }
                out.println("CMD_RESPONSE:" + response);

            } catch (IOException ex) {
                System.err.println("Error handling command: " + ex.getMessage());
            }
        }
    }

    private void sendTerminationCommand() {
        try (Socket sock = new Socket(masterHost, masterPort);
             PrintWriter w = new PrintWriter(sock.getOutputStream(), true)) {
            w.println("WORKER_SHUTDOWN:" + workerId);
            System.out.println("Sent shutdown to Master");
        } catch (IOException e) {
            System.err.println("Shutdown notify failed: " + e.getMessage());
        }
    }

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
        Worker worker = new Worker(12345,20000);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered for Worker");
            try {
                worker.sendTerminationCommand();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        worker.start();
    }
}