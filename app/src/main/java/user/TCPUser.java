package user;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * TCPClient demonstrates a simple client that connects to the MasterServer,
 * sends a message, and prints the response.
 */
public class TCPUser {
    // Server host and port settings.
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    /**
     * The main method connects to the MasterServer, sends a message, and prints the response.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))
        ) {
            // Send a message to the server.
            writer.println("Hello, MasterServer!");
            // Read and print the response from the server.
            String response = reader.readLine();
            System.out.println("Response from server: " + response);
        } catch (IOException ex) {
            System.err.println("Client exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
