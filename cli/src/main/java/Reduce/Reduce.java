package Reduce;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Reduce server that listens on a specific port and dispatches each
 * incoming connection to a ReduceHandler thread for processing.
 */
public class Reduce {
    /** Port on which this reduce server listens for connections. */
    public static final int REDUCE_PORT = 23456;

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