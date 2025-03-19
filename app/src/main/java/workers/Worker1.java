package workers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import model.Store;

public class Worker1 {

    private int port;
    // Map of store name to Store objects.
    private Map<String, Store> stores;

    public Worker1(int port) {
        this.port = port;
        this.stores = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Loads stores from the resource file "Stores.json" located in the "jsonf" package.
     */
    public void loadStores() {
        // Try loading the file using the class loader (without a leading slash)
        InputStream is = Worker1.class.getResourceAsStream("/jsonf/Stores.json");
        if (is == null) {
            System.err.println("Resource not found: jsonf/Stores.json");
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
            List<Store> storeList = gson.fromJson(jsonContent, new com.google.gson.reflect.TypeToken<List<Store>>(){}.getType());
            synchronized (stores) {
                for (Store s : storeList) {
                    stores.put(s.getStoreName(), s);
                }
            }
            System.out.println("Loaded " + storeList.size() + " stores from resource: jsonf/Stores.json");
        } catch (IOException e) {
            System.err.println("Error loading stores from resource: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                // Use the external WorkerClientHandler class (imported from package workers)
                WorkerClientHandler handler = new WorkerClientHandler(socket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting Worker: " + e.getMessage());
        }
    }

    /**
     * Processes a command and returns a response.
     * This method is used by WorkerClientHandler.
     */
    public String processCommand(String command, String data) {
        Gson gson = new Gson();
        if (command.equals("ADD_STORE")) {
            Store store = gson.fromJson(data, Store.class);
            synchronized (stores) {
                stores.put(store.getStoreName(), store);
            }
            return "Store " + store.getStoreName() + " added successfully.";
        } else if (command.equals("REMOVE_STORE")) {
            String storeName = data.trim();
            synchronized (stores) {
                if (stores.containsKey(storeName)) {
                    stores.remove(storeName);
                    return "Store " + storeName + " removed successfully.";
                } else {
                    return "Store " + storeName + " not found.";
                }
            }
        } else if (command.equals("PURCHASE_PRODUCT")) {
            // Expected data format: "storeName|productName|quantity"
            String[] parts = data.split("\\|");
            if (parts.length < 3) {
                return "Invalid data for PURCHASE_PRODUCT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            int quantity;
            try {
                quantity = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "Invalid quantity format.";
            }
            synchronized (stores) {
                Store store = stores.get(storeName);
                if (store != null) {
                    boolean success = store.purchaseProduct(productName, quantity);
                    if (success) {
                        return "Purchase successful for " + quantity + " of " + productName + " from store " + storeName + ".";
                    } else {
                        return "Purchase failed: insufficient stock or product not found.";
                    }
                } else {
                    return "Store " + storeName + " not found.";
                }
            }
        } else if (command.equals("ADD_PRODUCT")) {
            // Expected data format: "storeName|productJson"
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for ADD_PRODUCT.";
            }
            String storeName = parts[0];
            String productJson = parts[1];
            model.Product product = gson.fromJson(productJson, model.Product.class);
            synchronized (stores) {
                Store store = stores.get(storeName);
                if (store != null) {
                    store.addProduct(product);
                    return "Product " + product.getProductName() + " added to store " + storeName + ".";
                } else {
                    return "Store " + storeName + " not found.";
                }
            }
        } else if (command.equals("REMOVE_PRODUCT")) {
            // Expected data format: "storeName|productName"
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for REMOVE_PRODUCT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            synchronized (stores) {
                Store store = stores.get(storeName);
                if (store != null) {
                    boolean removed = store.removeProduct(productName);
                    if (removed) {
                        return "Product " + productName + " removed from store " + storeName + ".";
                    } else {
                        return "Product " + productName + " not found in store " + storeName + ".";
                    }
                } else {
                    return "Store " + storeName + " not found.";
                }
            }
        } else if (command.equals("LIST_STORES")) {
            synchronized (stores) {
                if (stores.isEmpty()) {
                    return "No stores available.";
                }
                StringBuilder sb = new StringBuilder();
                for (String name : stores.keySet()) {
                    sb.append(name).append(", ");
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 2); // Remove trailing comma and space
                }
                return sb.toString();
            }
        }
        return "Unknown command.";
    }

    public static void main(String[] args) {
        int port = 6000; // Default port.
        Worker1 worker = new Worker1(port);
        worker.loadStores();  // Loads stores from the resource file "Stores.json" in package "jsonf"
        worker.start();
    }
}
