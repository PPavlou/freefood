package user;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.Gson;
import model.Store;
import model.Product;

/**
 * The Manager class acts as a client application for the manager console.
 * It provides functionalities to add stores, remove stores, and to add or remove products.
 * The Manager communicates with the Master server via TCP sockets.
 */
public class Manager {
    private String masterHost;
    private int masterPort;

    /**
     * Constructs a Manager instance with the specified Master server host and port.
     *
     * @param masterHost the host address of the Master server.
     * @param masterPort the port number of the Master server.
     */
    public Manager(String masterHost, int masterPort) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    /**
     * Sends a command along with its associated data to the Master server.
     * The communication is performed over a TCP socket.
     *
     * @param command the command type (e.g., "ADD_STORE", "REMOVE_STORE", "ADD_PRODUCT",
     *                "REMOVE_PRODUCT", "LIST_STORES").
     * @param data the JSON data or additional parameters as a String.
     * @return the response received from the Master server.
     */
    private String sendCommand(String command, String data) {
        String response = "";
        try (Socket socket = new Socket(masterHost, masterPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send command and data to the Master server
            out.println(command);
            out.println(data);

            // Read response from the Master server
            response = in.readLine();
        } catch (IOException e) {
            System.err.println("Error communicating with Master server: " + e.getMessage());
        }
        return response;
    }

    /**
     * Adds a new store to the system by sending its JSON representation to the Master server.
     *
     * @param storeJson the JSON string representing the store.
     * @return the response from the Master server.
     */
    public String addStore(String storeJson) {
        return sendCommand("ADD_STORE", storeJson);
    }

    /**
     * Removes a store from the system by sending the store name (exact match) to the Master server.
     *
     * @param storeName the name of the store to remove.
     * @return the response from the Master server.
     */
    public String removeStore(String storeName) {
        return sendCommand("REMOVE_STORE", storeName);
    }

    /**
     * Adds a new product to an existing store.
     *
     * @param storeName   the name of the store where the product should be added.
     * @param productJson the JSON string representing the product.
     * @return the response from the Master server.
     */
    public String addProduct(String storeName, String productJson) {
        String data = storeName + "|" + productJson;
        return sendCommand("ADD_PRODUCT", data);
    }

    /**
     * Removes a product from an existing store.
     *
     * @param storeName   the name of the store.
     * @param productName the name of the product to remove.
     * @return the response from the Master server.
     */
    public String removeProduct(String storeName, String productName) {
        String data = storeName + "|" + productName;
        return sendCommand("REMOVE_PRODUCT", data);
    }

    /**
     * Lists all the store names available in the system.
     *
     * @return a comma-separated string of store names.
     */
    public String listStores() {
        return sendCommand("LIST_STORES", "");
    }

    /**
     * Interactive menu to allow the manager to add, remove, or list stores manually.
     */
    public static void interactiveMenu(Manager manager) {
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();
        while (true) {
            System.out.println("=== Manager Console ===");
            System.out.println("Select an option:");
            System.out.println("1. Add Store");
            System.out.println("2. Remove Store");
            System.out.println("3. List Stores");
            System.out.println("4. Exit");
            System.out.print("Choice: ");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) {
                // Interactive input for adding a store
                System.out.println("Enter store details:");

                System.out.print("Store Name: ");
                String storeName = scanner.nextLine();

                System.out.print("Latitude: ");
                double latitude = Double.parseDouble(scanner.nextLine());

                System.out.print("Longitude: ");
                double longitude = Double.parseDouble(scanner.nextLine());

                System.out.print("Food Category: ");
                String foodCategory = scanner.nextLine();

                System.out.print("Rating (stars, 1-5): ");
                int stars = Integer.parseInt(scanner.nextLine());

                System.out.print("Number of Votes: ");
                int noOfVotes = Integer.parseInt(scanner.nextLine());

                System.out.print("Logo Path: ");
                String storeLogo = scanner.nextLine();

                List<Product> products = new ArrayList<>();
                System.out.print("Number of products to add: ");
                int productCount = Integer.parseInt(scanner.nextLine());

                for (int i = 0; i < productCount; i++) {
                    System.out.println("Enter details for product " + (i + 1) + ":");
                    System.out.print("  Product Name: ");
                    String productName = scanner.nextLine();

                    System.out.print("  Product Type: ");
                    String productType = scanner.nextLine();

                    System.out.print("  Available Stock: ");
                    int availableAmount = Integer.parseInt(scanner.nextLine());

                    System.out.print("  Price: ");
                    double price = Double.parseDouble(scanner.nextLine());

                    Product product = new Product(productName, productType, availableAmount, price);
                    products.add(product);
                }

                // Build the Store object
                Store store = new Store();
                store.setStoreName(storeName);
                store.setLatitude(latitude);
                store.setLongitude(longitude);
                store.setFoodCategory(foodCategory);
                store.setStars(stars);
                store.setNoOfVotes(noOfVotes);
                store.setStoreLogo(storeLogo);
                store.setProducts(products);

                // Convert the Store object to JSON using Gson.
                String storeJson = gson.toJson(store);
                System.out.println("Generated JSON: " + storeJson);

                // Send the JSON to the Master server
                String response = manager.addStore(storeJson);
                System.out.println("Server Response: " + response);

                // Automatically list all available stores after the operation
                String listResponse = manager.listStores();
                System.out.println("Current Stores: " + listResponse);

            } else if (choice == 2) {
                // Interactive input for removing a store
                System.out.print("Enter the exact store name to remove: ");
                String storeName = scanner.nextLine();
                String response = manager.removeStore(storeName);
                System.out.println("Server Response: " + response);

                // Automatically list all available stores after the operation
                String listResponse = manager.listStores();
                System.out.println("Current Stores: " + listResponse);

            } else if (choice == 3) {
                // List current stores without any add/remove operation
                String listResponse = manager.listStores();
                System.out.println("Current Stores: " + listResponse);

            } else if (choice == 4) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Invalid option, please try again.");
            }
        }
        scanner.close();
    }

    /**
     * Main method for testing the Manager interactive console.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        Manager manager = new Manager("localhost", 12345);
        interactiveMenu(manager);
    }

}
