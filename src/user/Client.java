package user;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * DummyClient simulates a client application that connects to the MasterServer.
 * It sends a sample request (e.g., a search query or purchase request) to the server
 * and prints the response received from the MasterServer.
 */
public class Client {
    // The host and port where the MasterServer is running.
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    /**
     * Main method to run the DummyClient.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            // Example request: send a search query command to the server.
            String dummyRequest = "SEARCH: FoodCategory=pizzeria";
            System.out.println("Sending request: " + dummyRequest);
            writer.println(dummyRequest);

            // Read and print the response from the server.
            String response = reader.readLine();
            System.out.println("Response from server: " + response);

        } catch (IOException e) {
            System.err.println("DummyClient exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}