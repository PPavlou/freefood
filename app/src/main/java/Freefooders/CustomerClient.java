package Freefooders;

import java.io.*;
import java.net.Socket;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.Map;
import com.google.gson.*;

public class CustomerClient {
    // The host and port where the MasterServer is running.
    private String SERVER_HOST;
    private int SERVER_PORT;
    private double latitude;
    private double longitude;
    private int radius;

    public CustomerClient(String server_host,int server_port,double latitude,double longitude)
    {
        this.SERVER_HOST  = server_host;
        this.SERVER_PORT = server_port;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = 5;//default value
    }

    public CustomerClient(String server_host,int server_port,double latitude,double longitude,int radius)
    {
        this.SERVER_HOST  = server_host;
        this.SERVER_PORT = server_port;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }
    /**
     * Sends a command along with its data to the Master server.
     *
     * @param command the command type (e.g., "SEARCH", "PURCHASE_PRODUCT").
     * @param data the associated data as a String.
     * @return the response from the Master server.
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

    /**
     * Gets the latitude of the client.
     *
     * @return The latitude.
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude of the client.
     *
     * @param latitude The new latitude.
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * Gets the longitude of the client.
     *
     * @return The longitude.
     */
    public double getLongitude() {
        return this.longitude;
    }

    /**
     * Sets the longitude of the client.
     *
     * @param longitude The new longitude.
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Gets the radius of the client.
     *
     * @return The radius.
     */
    public int getRadius() {
        return this.radius;
    }

    /**
     * Sets the radius of the client.
     *
     * @param radius The new longitude.
     */
    public void setRadius(int radius) {
        this.radius = radius;
    }

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
     * Provides an interactive console for customer operations.
     */
    public void interactiveMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("=== Customer Console ===");
            System.out.println("Select an option:");
            System.out.println("1. Search Stores by Food Category");
            System.out.println("2. Search Stores by StarCategory");
            System.out.println("3. Search Stores by AvgPrice");
            System.out.println("4. Search Stores in "+this.getRadius()+"km radius");
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
                    int stars = getValidInteger(messageForStars,5);
                    String searchStarsResponse = sendCommand("SEARCH", "Stars=" + stars);
                    printPrettyResponse(searchStarsResponse);
                    break;
                case 3:
                    String messageForAvgPrice = "AvgPrice";
                    int avgPrice = getValidInteger(messageForAvgPrice,3);
                    String searchAvgPriceResponse = sendCommand("SEARCH", "AvgPrice=" + avgPrice);
                    printPrettyResponse(searchAvgPriceResponse);
                    break;
                case 4:
                    String messageForRadiusFilter = getRadius() + "," +getLongitude()+ "," +getLatitude();
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
                    if(purchaseResponse.contains("Successfully"))
                    {
                        String messageForReview = "Review";
                        int review = getValidInteger(messageForReview,5);
                        String reviewResponse = sendCommand("REVIEW", storeName + "|" + Integer.toString(review));
                        System.out.println(reviewResponse);
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

    public int getValidInteger(String message,int range)
    {
        Scanner scanner = new Scanner(System.in);
        int review = 0;
        boolean validInput = false;
        System.out.print("Select a " + message +  " (1-" + range + "): ");
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
        return  review;
    }

}
