package Reduce;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Reduce {
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
