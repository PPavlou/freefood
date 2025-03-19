package Freefooders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * DummyClient simulates a client application that connects to the MasterServer.
 * It sends sample requests (e.g., a search query and a purchase request) to the server
 * and prints the responses received from the MasterServer.
 */
public class Client {
    // The host and port where the MasterServer is running.
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    /**
     * Sends a command and its associated data to the Master server.
     *
     * @param command the command type (e.g., "SEARCH", "PURCHASE_PRODUCT").
     * @param data the associated data as a String.
     * @return the response from the Master server.
     */
    private static String sendCommand(String command, String data) {
        String response = "";
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            // Send command and data on separate lines.
            writer.println(command);
            writer.println(data);

            // Read and return the response.
            response = reader.readLine();
        } catch (IOException e) {
            System.err.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }

    public static void main(String[] args) {
        // Simulate a search query on the customer side.
        String searchResponse = sendCommand("SEARCH", "FoodCategory=pizzeria");
        System.out.println("Search Response: " + searchResponse);

        // Simulate a purchase (buy) request from the customer side.
        // Data format: "storeName|productName|quantity"
        String purchaseResponse = sendCommand("PURCHASE_PRODUCT", "PizzaWorld|Pepperoni|30");
        System.out.println("Purchase Response: " + purchaseResponse);
    }
}
