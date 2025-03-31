package Worker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import Manager.StoreManager;
import Manager.ProductManager;
import model.Store;
import model.Product;
import mapreduce.MapReduceFramework;
import mapreduce.SearchMapper;
import mapreduce.SearchReducer;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Worker {
    private int port;
    private StoreManager storeManager;
    private ProductManager productManager;

    // These will be set dynamically upon registration.
    private int workerId = -1;
    private int totalWorkers = 1;

    // Constructor: now we just pass the port.
    public Worker(int port) {
        this.port = port;
        this.storeManager = new StoreManager();
        this.productManager = new ProductManager();
    }

    public Worker() {
        this(12345);
    }

    /**
     * Loads this worker’s partition of stores from the JSON file.
     * The JSON file contains a list of stores.
     * Each store is assigned to a worker based on its index modulo totalWorkers.
     */
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
            Gson gson = new Gson();
            List<Store> allStores = gson.fromJson(jsonContent, new TypeToken<List<Store>>(){}.getType());
            List<Store> partitionStores = new ArrayList<>();
            // Make sure totalWorkers is > 0.
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

    /**
     * Connects to the Master, sends a handshake, receives assignment, and processes incoming commands.
     */
    public void start() {
        try {
            Socket socket = new Socket("localhost", port);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send handshake.
            writer.println("WORKER_HANDSHAKE");
            // Wait for assignment message.
            String assignMsg = reader.readLine();
            if (assignMsg != null && assignMsg.startsWith("WORKER_ASSIGN:")) {
                // Expected format: WORKER_ASSIGN:<workerId>:<totalWorkers>
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

            // Now that workerId and totalWorkers are set, load the partitioned data.
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
    /**
     * Processes a command from the Master over this worker’s local (partitioned) data.
     * For SEARCH, applies the SearchMapper (map phase only) and returns intermediate results as JSON.
     * For update/reporting commands, processes the command locally and returns a text message.
     */
    public String processCommand(String command, String data) {
        Gson gson = new Gson();
        if ("SEARCH".equalsIgnoreCase(command)) {
            // Expect "filterKey=filterValue"
            String[] parts = data.split("=", 2);
            if (parts.length != 2) {
                return "Invalid search query format. Expected key=value.";
            }
            String filterKey = parts[0].trim();
            String filterValue = parts[1].trim();
            List<MapReduceFramework.Pair<String, Store>> input = new ArrayList<>();
            Map<String, Store> localStores = storeManager.getAllStores();
            for (Map.Entry<String, Store> entry : localStores.entrySet()) {
                input.add(new MapReduceFramework.Pair<>(entry.getKey(), entry.getValue()));
            }
            // Use SearchMapper (which returns intermediate pairs) over the local data.
            mapreduce.SearchMapper mapper = new mapreduce.SearchMapper(filterKey, filterValue);
            List<MapReduceFramework.Pair<String, Store>> intermediate = new ArrayList<>();
            for (MapReduceFramework.Pair<String, Store> pair : input) {
                intermediate.addAll(mapper.map(pair.getKey(), pair.getValue()));
            }
            return gson.toJson(intermediate);
        } else if ("ADD_STORE".equalsIgnoreCase(command)) {
            Store store = gson.fromJson(data, Store.class);
            String result = storeManager.addStore(store);
            return result;
        } else if ("REMOVE_STORE".equalsIgnoreCase(command)) {
            String storeName = data.trim();
            String result = storeManager.removeStore(storeName);
            return result;
        } else if ("PURCHASE_PRODUCT".equalsIgnoreCase(command)) {
            // Format: "storeName|productName|quantity"
            String[] parts = data.split("\\|");
            if (parts.length < 3) {
                return "Invalid data for PURCHASE_PRODUCT.";
            }
            String storeName = parts[0].trim();
            String productName = parts[1].trim();
            int quantity;
            try {
                quantity = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return "Invalid quantity format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                boolean success = store.purchaseProduct(productName, quantity);
                if (success) {
                    return "Successfully purchased " + quantity + " of " + productName + " from store " + storeName + ".";
                } else {
                    return "Purchase failed: insufficient stock or product not found.";
                }
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("ADD_PRODUCT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for ADD_PRODUCT.";
            }
            String storeName = parts[0].trim();
            String productJson = parts[1].trim();
            Product product = gson.fromJson(productJson, Product.class);
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.addProduct(store, product);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("REMOVE_PRODUCT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for REMOVE_PRODUCT.";
            }
            String storeName = parts[0].trim();
            String productName = parts[1].trim();
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.removeProduct(store, productName);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("UPDATE_PRODUCT_AMOUNT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for UPDATE_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0].trim();
            String productName = parts[1].trim();
            int newAmount;
            try {
                newAmount = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return "Invalid quantity format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.updateProductAmount(store, productName, newAmount);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("INCREMENT_PRODUCT_AMOUNT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for INCREMENT_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0].trim();
            String productName = parts[1].trim();
            int increment;
            try {
                increment = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return "Invalid increment format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                for (Product product : store.getProducts()) {
                    if (product.getProductName().equals(productName)) {
                        int current = product.getAvailableAmount();
                        product.setAvailableAmount(current + increment);
                        return "Product " + productName + " amount increased by " + increment +
                                " in store " + storeName + ". New amount: " + (current + increment) + ".";
                    }
                }
                return "Product " + productName + " not found in store " + storeName + ".";
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("DECREMENT_PRODUCT_AMOUNT".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for DECREMENT_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0].trim();
            String productName = parts[1].trim();
            int decrement;
            try {
                decrement = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return "Invalid decrement format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.decrementProductAmount(store, productName, decrement);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("DELETED_PRODUCTS".equalsIgnoreCase(command)) {
            return productManager.getDeletedProductsReport();
        } else if ("LIST_STORES".equalsIgnoreCase(command)) {
            return storeManager.getAllStores().keySet().toString();
        } else if ("REVIEW".equalsIgnoreCase(command)) {
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for REVIEW.";
            }
            String storeName = parts[0].trim();
            int review;
            try {
                review = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return "Invalid review format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                store.updateStoreReviews(review);
                return storeName + " Reviews Updated: Stars = " + store.getStars();
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if ("AGGREGATE_SALES_BY_PRODUCT_NAME".equalsIgnoreCase(command)) {
            String[] parts = data.split("=", 2);
            if (parts.length != 2) {
                return "Invalid aggregation query format. Expected ProductName=<value>.";
            }
            String productNameQuery = parts[1].trim();
            ArrayList<String> results = new ArrayList<>();
            int overallTotal = 0;
            for (Store store : storeManager.getAllStores().values()) {
                Map<String, Store.SalesRecordEntry> sales = store.getSalesRecord();
                if (sales.containsKey(productNameQuery)) {
                    int qty = sales.get(productNameQuery).getQuantity();
                    String result = store.getStoreName() + ": " + productNameQuery + " = " + qty;
                    overallTotal += qty;
                    results.add(result);
                }
            }
            return "Overall Total Sales for Product " + productNameQuery + ": " + overallTotal + results;
        }
        return "Unknown command.";
    }

    private double calculateDistanceBetween2Points(double lon1, double lat1, double lon2, double lat2) {
        int R = 6371;
        double dLat = deg2rad(lat2 - lat1);
        double dLon = deg2rad(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double deg2rad(double deg) {
        return deg * (Math.PI / 180);
    }

    public static void main(String[] args) {
        // For testing, this Worker instance will connect to Master and then wait for commands.
        // In a real setup, you would launch a worker on each PC with its own configuration.
        Worker worker = new Worker(12345);
        worker.start();
    }
}
