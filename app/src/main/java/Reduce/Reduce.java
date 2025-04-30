package Reduce;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Reduce server that listens on a specific port and dispatches each
 * incoming connection to a ReduceHandler thread for processing.
 */
public class Reduce {

    /** Port on which this reduce server listens for connections. */
    public static final int REDUCE_PORT = 23456;

    /**
     * Main entry point for the reduce server.
     * Creates a ServerSocket bound to REDUCE_PORT, then continuously
     * accepts incoming connections and starts a new ReduceHandler thread
     * for each one.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(REDUCE_PORT)) {
            System.out.println("Reduce server listening on port " + REDUCE_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ReduceHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Reduce server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
