package Worker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import Manager.StoreManager;
import Manager.ProductManager;
import model.Store;
import model.Product;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Worker {
    private int port;
    // Instead of maintaining our own map, we delegate to a StoreManager.
    private StoreManager storeManager;
    // Handles product-specific operations.
    private ProductManager productManager;

    public Worker(int port) {
        this.port = port;
        this.storeManager = new StoreManager();
        this.productManager = new ProductManager();
    }

    // Default constructor using port 6000.
    public Worker() {
        this(6000);
    }

    /**
     * Loads stores from the JSON file located in resources.
     * The JSON is deserialized into a List of Store objects, and each store is added
     * to the StoreManager.
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
            List<Store> storeList = gson.fromJson(jsonContent, new TypeToken<List<Store>>(){}.getType());
            for (Store s : storeList) {
                storeManager.addStore(s);
            }
            System.out.println("Loaded " + storeList.size() + " stores from resource: /jsonf/Stores.json");
        } catch (IOException e) {
            System.err.println("Error loading stores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Starts the Worker server to accept connections from the Master.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                WorkerMasterHandler handler = new WorkerMasterHandler(socket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting Worker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processes a command received from the Master and returns an appropriate response.
     * Delegates store operations to StoreManager and product operations to ProductManager.
     */
    public String processCommand(String command, String data) {
        Gson gson = new Gson();
        if (command.equals("ADD_STORE")) {
            Store store = gson.fromJson(data, Store.class);
            return storeManager.addStore(store);
        } else if (command.equals("REMOVE_STORE")) {
            String storeName = data.trim();
            return storeManager.removeStore(storeName);
        } else if (command.equals("PURCHASE_PRODUCT")) {
            // Data format: "storeName|productName|quantity"
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
        } else if (command.equals("ADD_PRODUCT")) {
            // Data format: "storeName|productJson"
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for ADD_PRODUCT.";
            }
            String storeName = parts[0];
            String productJson = parts[1];
            Product product = gson.fromJson(productJson, Product.class);
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.addProduct(store, product);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if (command.equals("REMOVE_PRODUCT")) {
            // Data format: "storeName|productName"
            String[] parts = data.split("\\|", 2);
            if (parts.length < 2) {
                return "Invalid data for REMOVE_PRODUCT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.removeProduct(store, productName);
            } else {
                return "Store " + storeName + " not found.";
            }
        } else if (command.equals("UPDATE_PRODUCT_AMOUNT")) {
            // Data format: "storeName|productName|newAmount"
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for UPDATE_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            int newAmount;
            try {
                newAmount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "Invalid quantity format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.updateProductAmount(store, productName, newAmount);
            } else {
                return "Store " + storeName + " not found.";
            }
        }
        // Inside Worker.processCommand(...)
        else if (command.equals("INCREMENT_PRODUCT_AMOUNT")) {
            // Expected data format: "storeName|productName|incrementValue"
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for INCREMENT_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            int increment;
            try {
                increment = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "Invalid increment format.";
            }
            // Retrieve store from StoreManager.
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                for (Product product : store.getProducts()) {
                    if (product.getProductName().equals(productName)) {
                        int currentAmount = product.getAvailableAmount();
                        product.setAvailableAmount(currentAmount + increment);
                        return "Product " + productName + " amount increased by " + increment +
                                " in store " + storeName + ". New amount: " + (currentAmount + increment) + ".";
                    }
                }
                return "Product " + productName + " not found in store " + storeName + ".";
            } else {
                return "Store " + storeName + " not found.";
            }
        }
        else if (command.equals("DECREMENT_PRODUCT_AMOUNT")) {
            // Expected data format: "storeName|productName|decrementValue"
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return "Invalid data for DECREMENT_PRODUCT_AMOUNT.";
            }
            String storeName = parts[0];
            String productName = parts[1];
            int decrement;
            try {
                decrement = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "Invalid decrement format.";
            }
            Store store = storeManager.getStore(storeName);
            if (store != null) {
                return productManager.decrementProductAmount(store, productName, decrement);
            } else {
                return "Store " + storeName + " not found.";
            }
        }
        else if (command.equals("DELETED_PRODUCTS")) {
            return productManager.getDeletedProductsReport();
        } else if (command.equals("LIST_STORES")) {
            return storeManager.getAllStores().keySet().toString();
        }
        return "Unknown command.";
    }

    public static void main(String[] args) {
        Worker worker = new Worker(6000);
        worker.loadStores();
        worker.start();
    }
}
