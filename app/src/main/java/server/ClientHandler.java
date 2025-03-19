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
 * It forwards the client's command and data to a Worker node (on localhost:6000)
 * and returns the response back to the client.
 */
public class ClientHandler implements Runnable {
    private Socket socket;

    // Worker node details (assumed to be running on localhost:6000)
    private static final String WORKER_HOST = "localhost";
    private static final int WORKER_PORT = 6000;

    /**
     * Constructs a ClientHandler for the given client socket.
     *
     * @param socket the client's socket connection.
     */
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    /**
     * Processes the incoming TCP request by reading the command and data,
     * forwarding them to the Worker node, and sending back the Worker's response.
     */
    @Override
    public void run() {
        try (
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true)
        ) {
            // Read command and data from the client.
            String command = reader.readLine();
            String data = reader.readLine();
            System.out.println("Received command: " + command);
            System.out.println("Received data: " + data);

            // Forward the command and data to the Worker node.
            String response = forwardToWorker(command, data);

            // Send the response back to the client.
            writer.println(response);
        } catch (IOException ex) {
            System.err.println("ClientHandler exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore socket close exception.
            }
        }
    }

    /**
     * Forwards the given command and data to the Worker node and returns its response.
     *
     * @param command the command to forward.
     * @param data the associated data.
     * @return the response from the Worker node.
     */
    private String forwardToWorker(String command, String data) {
        String response = "";
        try (Socket workerSocket = new Socket(WORKER_HOST, WORKER_PORT);
             PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()))
        ) {
            // Send command and data to the Worker.
            out.println(command);
            out.println(data);
            // Read and return the response from the Worker.
            response = in.readLine();
        } catch (IOException e) {
            response = "Error connecting to worker: " + e.getMessage();
            System.err.println(response);
        }
        return response;
    }
}
