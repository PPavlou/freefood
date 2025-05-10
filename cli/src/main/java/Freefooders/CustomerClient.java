package Freefooders;

import java.io.*;
import java.net.Socket;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.Map;
import com.google.gson.*;

/**
 * Client for Freefooders that allows a customer to search for stores by food category,
 * star rating, average price, or radius; purchase products; and submit reviews.
 * Communicates with the MasterServer over TCP sockets.
 */
public class CustomerClient {
    // The host and port where the MasterServer is running.
    private String SERVER_HOST;
    private int SERVER_PORT;
    private double latitude;
    private double longitude;
    private int radius;

    /**
     * Creates a CustomerClient with a default radius of 5 km.
     *
     * @param server_host the server hostname
     * @param server_port the server port number
     * @param latitude    initial latitude
     * @param longitude   initial longitude
     */
    public CustomerClient(String server_host, int server_port, double latitude, double longitude) {
        this.SERVER_HOST  = server_host;
        this.SERVER_PORT = server_port;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = 5; //default value
    }

    /**
     * Creates a CustomerClient with a specified search radius.
     *
     * @param server_host the server hostname
     * @param server_port the server port number
     * @param latitude    initial latitude
     * @param longitude   initial longitude
     * @param radius      search radius in kilometers
     */
    public CustomerClient(String server_host, int server_port, double latitude, double longitude, int radius) {
        this.SERVER_HOST  = server_host;
        this.SERVER_PORT = server_port;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    /**
     * Sends a command with its payload to the MasterServer and returns the response.
     *
     * @param command the command type (e.g., "SEARCH", "PURCHASE_PRODUCT")
     * @param data    the payload string for the command
     * @return the server's response, or an empty string on error
     */
    private String sendCommand(String command, String data) {
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

    /** Gets the client's current latitude. */
    public double getLatitude() {
        return latitude;
    }

    /** Sets the client's latitude. */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /** Gets the client's current longitude. */
    public double getLongitude() {
        return this.longitude;
    }

    /** Sets the client's longitude. */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /** Gets the client's search radius in kilometers. */
    public int getRadius() {
        return this.radius;
    }

    /** Sets the client's search radius in kilometers. */
    public void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * Parses a raw JSON response, expands nested JSON elements, and prints it
     * in pretty-printed format. If the response is not valid JSON, prints the
     * original string.
     *
     * @param jsonResponse the raw response from the server
     */
    private void printPrettyResponse(String jsonResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            // Parse the raw response string as a JSON object.
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            // Iterate over entries to check if any value is a nested JSON string.
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                try {
                    // Attempt to parse the value; if it’s a nested JSON, replace the string value.
                    JsonElement nested = JsonParser.parseString(entry.getValue().getAsString());
                    jsonObject.add(entry.getKey(), nested);
                } catch (Exception e) {
                    // If parsing fails, leave the entry as-is.
                }
            }
            System.out.println("Search Response:\n" + gson.toJson(jsonObject));
        } catch (Exception ex) {
            // If not a valid JSON, output the original response.
            System.out.println("Search Response:\n" + jsonResponse);
        }
    }

    /**
     * Launches an interactive console menu to allow the user to search stores,
     * purchase products, submit reviews, or exit the console.
     */
    public void interactiveMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("=== Customer Console ===");
            System.out.println("Select an option:");
            System.out.println("1. Search Stores by Food Category");
            System.out.println("2. Search Stores by StarCategory");
            System.out.println("3. Search Stores by AvgPrice");
            System.out.println("4. Search Stores in " + this.getRadius() + "km radius");
            System.out.println("5. Purchase Product");
            System.out.println("6. Exit");
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
                    printPrettyResponse(searchResponse);
                    break;
                case 2:
                    String messageForStars = "Star";
                    int stars = getValidInteger(messageForStars, 5);
                    String searchStarsResponse = sendCommand("SEARCH", "Stars=" + stars);
                    printPrettyResponse(searchStarsResponse);
                    break;
                case 3:
                    String messageForAvgPrice = "AvgPrice";
                    int avgPrice = getValidInteger(messageForAvgPrice, 3);
                    String searchAvgPriceResponse = sendCommand("SEARCH", "AvgPrice=" + avgPrice);
                    printPrettyResponse(searchAvgPriceResponse);
                    break;
                case 4:
                    String messageForRadiusFilter = getRadius() + "," + getLongitude() + "," + getLatitude();
                    String searchRadiusResponse = sendCommand("SEARCH", "Radius=" + messageForRadiusFilter);
                    printPrettyResponse(searchRadiusResponse);
                    break;
                case 5:
                    System.out.print("Enter Store Name: ");
                    String storeName = scanner.nextLine();
                    System.out.print("Enter Product Name: ");
                    String productName = scanner.nextLine();
                    System.out.print("Enter Quantity: ");
                    String quantity = scanner.nextLine();
                    String purchaseResponse = sendCommand("PURCHASE_PRODUCT", storeName + "|" + productName + "|" + quantity);
                    printPrettyResponse(purchaseResponse);
                    if (purchaseResponse.contains("Successfully")) {
                        String messageForReview = "Review";
                        int review = getValidInteger(messageForReview, 5);
                        String reviewResponse = sendCommand("REVIEW", storeName + "|" + Integer.toString(review));
                        printPrettyResponse(reviewResponse);
                    }
                    break;
                case 6:
                    System.out.println("Exiting Customer Console.");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    /**
     * Prompts the user to enter an integer between 1 and {@code range}, inclusive.
     * Repeats until valid input is provided.
     *
     * @param message label for the value being requested (e.g., "Stars", "Review")
     * @param range   maximum valid value
     * @return the validated integer entered by the user
     */
    public int getValidInteger(String message, int range) {
        Scanner scanner = new Scanner(System.in);
        int review = 0;
        boolean validInput = false;
        System.out.print("Select a " + message + " (1-" + range + "): ");
        while (!validInput) {
            try {
                review = scanner.nextInt();
                if (review < 1 || review > range) {
                    System.out.print(message + " must have values between 1 and " + range + ": ");
                } else {
                    validInput = true;
                }
            } catch (InputMismatchException e) {
                System.out.print("Please enter a valid integer: ");
                scanner.next(); // Consume the invalid input
            }
        }
        return review;
    }
}
