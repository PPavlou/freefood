package user;
import java.io.*;
import java.net.*;

/**
 * The Manager class acts as a client application for the manager console.
 * It provides functionalities to add stores and to add or remove products.
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
     * @param command the command type (e.g., "ADD_STORE", "ADD_PRODUCT", "REMOVE_PRODUCT").
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
     * Adds a new product to an existing store.
     * Combines the store name and product JSON data in a simple protocol and sends it to the Master server.
     *
     * @param storeName   the name of the store where the product should be added.
     * @param productJson the JSON string representing the product.
     * @return the response from the Master server.
     */
    public String addProduct(String storeName, String productJson) {
        // Use a simple delimiter (|) to separate the store name from the product JSON.
        String data = storeName + "|" + productJson;
        return sendCommand("ADD_PRODUCT", data);
    }

    /**
     * Removes a product from an existing store.
     * Sends the store name and the product name to remove to the Master server.
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
     * Main method for testing the Manager functionalities.
     * It demonstrates adding a store, adding a product, and removing a product.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        // Create a Manager instance connecting to the Master server on localhost:5000
        Manager manager = new Manager("localhost", 12345);

        // Example JSON for a store
        String storeJson = "{ \"StoreName\": \"PizzaWorld\", \"Latitude\": 37.991234, \"Longitude\": 23.732111, "
                + "\"FoodCategory\": \"pizzeria\", \"Stars\": 4, \"NoOfVotes\": 22, "
                + "\"StoreLogo\": \"/usr/bin/images/pizzaworld.png\", \"Products\": [] }";
        String response = manager.addStore(storeJson);
        System.out.println("Add Store Response: " + response);

        // Example JSON for a product
        String productJson = "{ \"ProductName\": \"Pepperoni\", \"ProductType\": \"pizza\", \"Available Amount\": 120, \"Price\": 10.5 }";
        response = manager.addProduct("PizzaWorld", productJson);
        System.out.println("Add Product Response: " + response);

        // Remove product example
        response = manager.removeProduct("PizzaWorld", "Pepperoni");
        System.out.println("Remove Product Response: " + response);
    }
}

