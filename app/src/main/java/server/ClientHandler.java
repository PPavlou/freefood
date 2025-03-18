package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientHandler processes client requests in a separate thread.
 */
public class ClientHandler implements Runnable {
    private Socket socket;

    /**
     * Constructs a ClientHandler for the given client socket.
     *
     * @param socket the client's socket connection.
     */
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    /**
     * Processes the client request by reading input and sending a response back.
     */
    @Override
    public void run() {
        try (
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true)
        ) {
            // Read the client's request (for example, a command or JSON data)
            String request = reader.readLine();
            System.out.println("Received: " + request);

            // Process the request – in a full application, here the command would be parsed
            // and forwarded to the appropriate components (e.g., to Worker nodes).
            String response = "Server received: " + request;

            // Send the response back to the client.
            writer.println(response);
        } catch (IOException ex) {
            System.err.println("ClientHandler exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore errors during socket close.
            }
        }
    }
}
