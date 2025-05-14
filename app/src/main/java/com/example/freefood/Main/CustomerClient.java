package com.example.freefood.Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client for Freefooders that allows a customer to search for stores by food category,
 * star rating, average price, or radius; purchase products; and submit reviews.
 * Communicates with the MasterServer over TCP sockets.
 * Adapted for Android.
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
        this.SERVER_HOST = server_host;
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
        this.SERVER_HOST = server_host;
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
    public String sendCommand(String command, String data) {
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
            e.printStackTrace();
            return "Error: " + e.getMessage();
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

    /** Gets the server host */
    public String getSERVER_HOST() {
        return SERVER_HOST;
    }

    /** Gets the server port */
    public int getSERVER_PORT() {
        return SERVER_PORT;
    }

    /**
     * Parses a raw JSON response, expands nested JSON elements, and returns it
     * in pretty-printed format. If the response is not valid JSON, returns the
     * original string.
     *
     * @param jsonResponse the raw response from the server
     * @return the pretty-printed JSON or the original string if not valid JSON
     */
    public String getPrettyResponse(String jsonResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            // Parse the raw response string as a JSON object.
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            // Iterate over entries to check if any value is a nested JSON string.
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                try {
                    // Attempt to parse the value; if it's a nested JSON, replace the string value.
                    JsonElement nested = JsonParser.parseString(entry.getValue().getAsString());
                    jsonObject.add(entry.getKey(), nested);
                } catch (Exception e) {
                    // If parsing fails, leave the entry as-is.
                }
            }
            return gson.toJson(jsonObject);
        } catch (Exception ex) {
            // If not a valid JSON, output the original response.
            return jsonResponse;
        }
    }
}