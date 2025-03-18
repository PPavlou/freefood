package workers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import model.Store;
import model.Product;

/**
 * The Worker1 class represents a worker node in a distributed online food delivery system.
 * A Worker listens for TCP requests from the Master server and processes commands such as:
 * <ul>
 *   <li>ADD_STORE - Adds a new store to the worker's memory.</li>
 *   <li>REMOVE_STORE - Removes a store from the worker's memory.</li>
 *   <li>ADD_PRODUCT - Adds a product to an existing store.</li>
 *   <li>REMOVE_PRODUCT - Removes a product from an existing store.</li>
 *   <li>PURCHASE_PRODUCT - Processes a purchase request.</li>
 *   <li>LIST_STORES - Returns a comma-separated list of store names.</li>
 * </ul>
 *
 * Workers are designed to be multithreaded, so that they can handle multiple requests concurrently.
 * The worker's store data is maintained in memory.
 */
public class Worker1 {

    private int port;
    // Stores assigned to this Worker, using the store name as key.
    private Map<String, Store> stores;

    /**
     * Constructs a Worker1 instance that will listen on the specified port.
     *
     * @param port The port number on which the Worker will listen for incoming TCP connections.
     */
    public Worker1(int port) {
        this.port = port;
        this.stores = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Starts the Worker server. The Worker continuously listens for incoming TCP connections
     * on the specified port and spawns a new thread to handle each connection.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting Worker: " + e.getMessage());
        }
    }

    /**
     * The ClientHandler class processes a single request from the Master server.
     */
    private class ClientHandler implements Runnable {
        private Socket socket;

        /**
         * Constructs a ClientHandler for a given client socket.
         *
         * @param socket The client socket connected to this Worker.
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Processes the incoming TCP request, handling the command and sending a response back.
         */
        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                // Read command and data from the Master server.
                String command = in.readLine();
                String data = in.readLine();
                System.out.println("Received Command: " + command);
                System.out.println("Data: " + data);

                // Process the command.
                String response = processCommand(command, data);
                out.println(response);
            } catch (IOException e) {
                System.err.println("Error handling request: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore exception during socket close.
                }
            }
        }
    }

    /**
     * Processes the command received from the Master server.
     *
     * Supported commands:
     * <ul>
     *   <li>ADD_STORE - expects a JSON string representing a store.</li>
     *   <li>REMOVE_STORE - expects the store name as a string.</li>
     *   <li>PURCHASE_PRODUCT - expects data in the format "storeName|productName|quantity".</li>
     *   <li>ADD_PRODUCT - expects data in the format "storeName|productJson".</li>
     *   <li>REMOVE_PRODUCT - expects data in the format "storeName|productName".</li>
     *   <li>LIST_STORES - no additional data; returns a comma-separated list of store names.</li>
     * </ul>
     *
     * @param command The command to execute.
     * @param data The associated data in String format.
     * @return A response message indicating the result of the operation.
     */
    private String processCommand(String command, String data) {
        if (command.equals("ADD_STORE")) {
            Store store = parseStore(data);
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
            Product product = parseProduct(productJson);
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

    /**
     * Parses a JSON string representing a store and returns a Store object.
     * Uses Gson for proper parsing.
     *
     * @param jsonData The JSON string representing the store.
     * @return A Store object created from the provided JSON data.
     */
    private Store parseStore(String jsonData) {
        Gson gson = new Gson();
        return gson.fromJson(jsonData, Store.class);
    }

    /**
     * Parses a JSON string representing a product and returns a Product object.
     * Uses Gson for proper parsing.
     *
     * @param jsonData The JSON string representing the product.
     * @return A Product object created from the provided JSON data.
     */
    private Product parseProduct(String jsonData) {
        Gson gson = new Gson();
        return gson.fromJson(jsonData, Product.class);
    }

    /**
     * Main method to start the Worker node.
     * The port number can be provided as a command-line argument.
     *
     * @param args Command-line arguments; the first argument is expected to be the port number.
     */
    public static void main(String[] args) {
        int port = 6000; // Default port.
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default port 6000.");
            }
        }
        Worker1 worker = new Worker1(port);
        worker.start();
    }
}
