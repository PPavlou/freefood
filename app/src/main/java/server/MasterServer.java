package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * MasterServer sets up a TCP server that listens for incoming client connections.
 * For each connection, it spawns a new thread (ClientHandler) to handle communication.
 */
public class MasterServer {
    // Define the port number for the TCP server.
    private static final int PORT = 12345;

    /**
     * The main method starts the Master Server and continuously listens for incoming connections.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Server is listening on port " + PORT);
            // Continuously accept new client connections.
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                // Create and start a new thread to handle the connected client.
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException ex) {
            System.err.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * ClientHandler processes client requests in a separate thread.
     */
    private static class ClientHandler implements Runnable {
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
}

