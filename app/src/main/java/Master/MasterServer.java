package Master;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.DistributedMapReduceJob;
import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import model.Store;
import java.util.*;

public class MasterServer {
    private static final int MASTER_PORT = 12345;
    // Thread-safe list for worker sockets.
    public static List<Socket> workerSockets = Collections.synchronizedList(new ArrayList<>());
    // A counter for worker registrations.
    private static int workerCount = 0;
    // Dedicated monitor object for worker availability.
    public static final Object workerAvailable = new Object();

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(MASTER_PORT);
            System.out.println("Master Server listening on port " + MASTER_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                // Set a short timeout for reading the initial handshake.
                socket.setSoTimeout(1000);

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
                    // Add the worker socket to the list and notify waiting threads.
                    synchronized (workerAvailable) {
                        workerSockets.add(socket);
                        workerAvailable.notifyAll();
                    }
                    System.out.println("Worker assigned ID " + assignedId + " from " + socket.getInetAddress());

                    // Trigger rebalancing: Every time a new worker connects, instruct all workers to reload
                    rebalanceStores();
                } else {
                    // This connection is from a client (manager or customer).
                    // Cancel the short timeout for the client.
                    socket.setSoTimeout(0);
                    // Spawn a new thread to handle client/manager requests.
                    ActionForClients clientHandler = new ActionForClients(socket, workerSockets, firstLine, reader);
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

    /**
     * Loads all store files, partitions them among the currently available workers,
     * and sends a RELOAD_STORES command to each worker with its assigned store list as JSON.
     */
    private static void rebalanceStores() {
        // Load all store JSON files (the same files as used by Worker).
        List<Store> allStores = new ArrayList<>();
        String[] storeFiles = {
                "/jsonf/stores/PizzaWorld.json",
                "/jsonf/stores/CoffeeCorner.json",
                "/jsonf/stores/SouvlakiKing.json",
                "/jsonf/stores/BurgerZone.json",
                "/jsonf/stores/BakeryDelight.json",
                "/jsonf/stores/AsiaFusion.json",
                "/jsonf/stores/TacoPlace.json",
                "/jsonf/stores/SeaFoodExpress.json",
                "/jsonf/stores/VeganGarden.json",
                "/jsonf/stores/SweetTooth.json"
        };
        Gson gson = new Gson();
        for (String fileName : storeFiles) {
            try (InputStream is = MasterServer.class.getResourceAsStream(fileName)) {
                if (is == null) {
                    System.err.println("Resource not found: " + fileName);
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String jsonContent = sb.toString();
                Store store = gson.fromJson(jsonContent, Store.class);
                if (store != null) {
                    allStores.add(store);
                }
            } catch (IOException e) {
                System.err.println("Error loading store from " + fileName + ": " + e.getMessage());
            }
        }

        // Partition the stores among available workers.
        Map<Socket, List<Store>> assignment = new HashMap<>();
        int workerCountLocal;
        synchronized (workerAvailable) {
            workerCountLocal = workerSockets.size();
            for (Socket ws : workerSockets) {
                assignment.put(ws, new ArrayList<>());
            }
        }
        for (int i = 0; i < allStores.size(); i++) {
            int index = i % workerCountLocal;
            // Assuming workerSockets list order corresponds to assignment order.
            Socket ws;
            synchronized (workerAvailable) {
                ws = workerSockets.get(index);
            }
            assignment.get(ws).add(allStores.get(i));
        }

        // Send the RELOAD_STORES command to each worker.
        for (Map.Entry<Socket, List<Store>> entry : assignment.entrySet()) {
            Socket ws = entry.getKey();
            String jsonStores = gson.toJson(entry.getValue());
            try {
                PrintWriter out = new PrintWriter(ws.getOutputStream(), true);
                out.println("RELOAD_STORES");
                out.println(jsonStores);
                // Optionally, read an acknowledgement.
            } catch (IOException e) {
                System.err.println("Error sending RELOAD_STORES to worker: " + e.getMessage());
            }
        }
        System.out.println("Rebalancing complete: assigned " + allStores.size() + " stores among " + workerCountLocal + " workers.");
    }
}
