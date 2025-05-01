package Freefooders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * The Client class serves as the entry point for the Freefooders
 * customer application. It establishes a connection to the MasterServer
 * and launches an interactive menu for processing user requests.
 */
public class Client {

    /**
     * The hostname where the MasterServer is listening.
     */
    private static final String SERVER_HOST = "172.20.10.2";

    /**
     * The port number on which the MasterServer is running.
     */
    private static final int SERVER_PORT = 12345;

    /**
     * Main entry point for the client application.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Instantiate a CustomerClient with the server’s host, port, and user location
        CustomerClient customerClient = new CustomerClient(SERVER_HOST, SERVER_PORT,37.994124,23.732089);

        // Start the interactive menu to handle user commands
        customerClient.interactiveMenu();
    }
}
