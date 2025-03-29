package Worker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import Manager.StoreManager;
import Manager.ProductManager;
import model.Store;
import model.Product;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        this(12345);
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
                s.setAveragePriceOfStore();
                s.setAveragePriceOfStoreSymbol();
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
        try {
            // Connect to the master server on the shared port.
            Socket socket = new Socket("localhost", port);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send a handshake to identify as a worker.
            writer.println("WORKER_HANDSHAKE");
            System.out.println("Worker connected to master on port " + port + " as WORKER");

            // Loop indefinitely to receive commands from the master.
            while (true) {
                String command = reader.readLine();
                if (command == null) {
                    System.out.println("Master closed the connection.");
                    break;
                }
                String data = reader.readLine();
                if (data == null) {
                    System.out.println("Master closed the connection.");
                    break;
                }
                System.out.println("Worker received command: " + command);
                System.out.println("Worker received data: " + data);

                // Process the command.
                String response = processCommand(command, data);

                // Send the response back.
                writer.println(response);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Error in persistent worker connection: " + e.getMessage());
            e.printStackTrace();
        }
    }





    /**
     * Processes a command received from the Master and returns an appropriate response.
     * Delegates store operations to StoreManager and product operations to ProductManager.
     */
    public String processCommand(String command, String data) {
        Gson gson = new Gson();
        if (command.equals("SEARCH")) {
            // Expect data in the format "FoodCategory=pizzeria"
            String[] parts = data.split("=");
            if (parts.length != 2) {
                return "Invalid search query format. Expected key=value.";
            }
            String key = parts[0].trim();
            String value = parts[1].trim();

            if (key.equalsIgnoreCase("FoodCategory") ||
                    key.equalsIgnoreCase("Stars") ||
                    key.equalsIgnoreCase("AvgPrice") ||
                    key.equalsIgnoreCase("Radius")){
                StringBuilder result = new StringBuilder();
                for (Store store : storeManager.getAllStores().values()) {
                    switch (key) {
                        case "FoodCategory":
                            if (store.getFoodCategory().equalsIgnoreCase(value)) {
                                result.append(store.toString()).append("\n");
                            }
                            break;
                        case "Stars":
                            if (Integer.toString(store.getStars()).equals(value)) {
                                result.append(store.toString()).append("\n");
                            }
                            break;
                        case "AvgPrice":
                            if (store.getAveragePriceOfStoreSymbol().equals("$".repeat(Integer.parseInt(value))))
                            {
                                result.append(store.toString()).append("\n");
                            }
                            break;
                        case "Radius":
                            double storeLongitude = store.getLongitude();
                            double storeLatitude = store.getLatitude();
                            String[] partsRadius = parts[1].split(",");
                            if (partsRadius.length != 3) {
                                System.out.println(parts[1]);
                                return "Invalid search query format. Expected radius,longtitude,latitude.";
                            }
                            int radius = Integer.parseInt(partsRadius[0]);
                            double clientLongitude = Double.parseDouble(partsRadius[1]);
                            double clientLatitude = Double.parseDouble(partsRadius[2]);
                            if (calculateDistanceBetween2Points(storeLongitude,storeLatitude,clientLongitude,clientLatitude) <= radius)
                            {
                                result.append(store.toString()).append("\n");
                            }
                            break;
                    }
                }
                if (result.length() == 0) {
                    return "No stores found for "+ key +": " + value;
                }
                return result.toString();
            } else {
                return "Search key not supported.";
            }
        } else if (command.equals("ADD_STORE")) {
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
        else if (command.equals("REVIEW")) {
            String[] parts = data.split("\\|", 2);
            String storeName = parts[0];
            int review = Integer.parseInt(parts[1]);

            Store store = storeManager.getStore(storeName);
            store.updateStoreReviews(review);
            String response = storeName + "Reviews Updated: Stars = " + store.getStars();
            return response;

        } else if (command.equals("AGGREGATE_SALES_BY_PRODUCT_NAME")) {
            // Expect data in the format "ProductName=Pepperoni"
            String[] parts = data.split("=");
            if (parts.length != 2) {
                return "Invalid aggregation query format. Expected ProductName=<value>.";
            }
            String productNameQuery = parts[1].trim();
            ArrayList<String> results = new ArrayList<String>(); // Create an ArrayList object
            int overallTotal = 0;
            // Iterate through all stores
            for (Store store : storeManager.getAllStores().values()) {
                Map<String, Store.SalesRecordEntry> sales = store.getSalesRecord();
                // Check if this store has sales for the requested product name
                if (sales.containsKey(productNameQuery)) {
                    int qty = sales.get(productNameQuery).getQuantity();
                    String result;
                    result = store.getStoreName()+": "+productNameQuery+" = "+qty;
                    overallTotal += qty;
                    results.add(result);
                }
            }
//            result.append("Overall Total Sales for Product '")
//                    .append(productNameQuery)
//                    .append("': ").append(overallTotal);
            return "Overall Total Sales for Product "+productNameQuery+": "+overallTotal+results;
        }
        return "Unknown command.";
    }

    private double calculateDistanceBetween2Points(double longitude1,double latitude1,double longitude2,double latitude2)
    {
        var R = 6371; // Radius of the earth in km
        double dLat = deg2rad(latitude2-latitude1);
        double dLon = deg2rad(longitude2-longitude1);
        double a =
                Math.sin(dLat/2) * Math.sin(dLat/2) +
                        Math.cos(deg2rad(latitude1)) * Math.cos(deg2rad(latitude2)) *
                                Math.sin(dLon/2) * Math.sin(dLon/2)
                ;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c; // Distance in km
        return d;
    }

    private double deg2rad(double deg) {
        return deg * (Math.PI/180);
    }

    public static void main(String[] args) {
        Worker worker = new Worker(12345);
        worker.loadStores();
        worker.start();
    }
}