package Freefooders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class CustomerClient {
    // The host and port where the MasterServer is running.
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    /**
     * Sends a command along with its data to the Master server.
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

            // Send the command and data on separate lines.
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

    /**
     * Provides an interactive console for customer operations.
     */
    public static void interactiveMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("=== Customer Console ===");
            System.out.println("Select an option:");
            System.out.println("1. Search Stores by Food Category");
            System.out.println("2. Purchase Product");
            System.out.println("3. Exit");
            System.out.print("Choice: ");
            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid option. Please enter a number.");
                continue;
            }
            switch (choice) {
                case 1:
                    System.out.print("Enter Food Category (e.g., pizzeria): ");
                    String category = scanner.nextLine();
                    String searchResponse = sendCommand("SEARCH", "FoodCategory=" + category);
                    System.out.println("Search Response: " + searchResponse);
                    break;
                case 2:
                    System.out.print("Enter Store Name: ");
                    String storeName = scanner.nextLine();
                    System.out.print("Enter Product Name: ");
                    String productName = scanner.nextLine();
                    System.out.print("Enter Quantity: ");
                    String quantity = scanner.nextLine();
                    String purchaseResponse = sendCommand("PURCHASE_PRODUCT", storeName + "|" + productName + "|" + quantity);
                    System.out.println("Purchase Response: " + purchaseResponse);
                    break;
                case 3:
                    System.out.println("Exiting Customer Console.");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    public static void main(String[] args) {
        interactiveMenu();
    }
}
