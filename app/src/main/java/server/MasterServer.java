package server;

import java.io.IOException;
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
}
