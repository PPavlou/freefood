package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.ArrayList;
import java.util.List;
import Worker.WorkerInfo;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // We'll store the persistent worker connection here.
    // In a more robust system you might use a list or map, but here one is sufficient.
    public static Socket workerSocket = null;

    public static void main(String[] args) {
        // Create an ExecutorService with a fixed thread pool
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socket.setSoTimeout(1000); // short timeout to read handshake

                // Peek at the first line to see if this is a worker handshake.
                String firstLine = reader.readLine();
                if ("WORKER_HANDSHAKE".equals(firstLine)) {
                    // This connection is from a worker. Save it for forwarding commands.
                    workerSocket = socket;
                    System.out.println("Persistent worker connection registered.");
                    // Optionally, you could start a thread to monitor the worker connection here.
                } else {
                    // This is a manager connection.
                    // Since we've already read the first line, pass it along.
                    ClientHandler clientHandler = new ClientHandler(socket, workerSocket, firstLine, reader);
                    executor.execute(clientHandler);
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown the executor when the server is shutting down
            executor.shutdown();
        }
    }
}

