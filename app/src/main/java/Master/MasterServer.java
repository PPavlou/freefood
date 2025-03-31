package Master;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // Thread-safe list for worker sockets.
    public static List<Socket> workerSockets = Collections.synchronizedList(new ArrayList<>());
    // A counter for worker registrations.
    private static int workerCount = 0;

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(MASTER_PORT);
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                socket.setSoTimeout(1000); // short timeout to read handshake

                // Read the first line to determine if this is a worker handshake.
                String firstLine = reader.readLine();
                if ("WORKER_HANDSHAKE".equals(firstLine)) {
                    // Assign a worker ID and update count.
                    int assignedId;
                    synchronized (MasterServer.class) {
                        assignedId = workerCount;
                        workerCount++;
                    }
                    // Inform the worker of its assignment:
                    // Format: WORKER_ASSIGN:<assignedId>:<current total workers>
                    writer.println("WORKER_ASSIGN:" + assignedId + ":" + workerCount);
                    // Add the socket to the list.
                    workerSockets.add(socket);
                    System.out.println("Worker assigned ID " + assignedId + " from " + socket.getInetAddress());
                } else {
                    // This is a client (manager) connection.
                    ClientHandler clientHandler = new ClientHandler(socket, workerSockets, firstLine, reader);
                    new Thread(clientHandler).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Master Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException e) { }
            }
        }
    }
}
