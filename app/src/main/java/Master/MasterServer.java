package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import Worker.WorkerInfo;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // We'll store the persistent worker connection here.
    public static Socket workerSocket = null;

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(MASTER_PORT);
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socket.setSoTimeout(1000); // short timeout to read handshake

                // Peek at the first line to see if this is a worker handshake.
                String firstLine = reader.readLine();
                if ("WORKER_HANDSHAKE".equals(firstLine)) {
                    // This connection is from a worker.
                    workerSocket = socket;
                    System.out.println("Persistent worker connection registered.");
                } else {
                    // This is a manager connection.
                    // Since we've already read the first line, pass it along.
                    ClientHandler clientHandler = new ClientHandler(socket, workerSocket, firstLine, reader);
                    // Instead of using ExecutorService, we create and start a new Thread.
                    new Thread(clientHandler).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch(IOException e) {
                    // Ignore closing exception.
                }
            }
        }
    }
}
