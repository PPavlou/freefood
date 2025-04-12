package Freefooders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * DummyClient simulates a client application that connects to the MasterServer.
 * It sends sample requests (e.g., a search query and a purchase request) to the server
 * and prints the responses received from the MasterServer.
 */
public class Client {
    // The host and port where the MasterServer is running.
    private static final String SERVER_HOST = "192.168.1.13";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        CustomerClient customerClient = new CustomerClient(SERVER_HOST, SERVER_PORT,37.994124,23.732089);
        customerClient.interactiveMenu();
    }
}
