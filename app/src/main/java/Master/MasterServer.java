// MasterServer.java
package Master;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import com.google.gson.Gson;
import model.Store;

public class MasterServer {
    private static final int MASTER_PORT = 12345;

    // Store user sessions for login and register
    public static final Map<String,String> userCredentials =
            Collections.synchronizedMap(new HashMap<>());
    public static final Map<String,String> userSessions =
            Collections.synchronizedMap(new HashMap<>());

    // Persist users
    private static final File CRED_STORE = new File("app/src/main/resources/users.json");

    static {
        // Load any previously saved credentials, or seed with a default user
        if (CRED_STORE.exists()) {
            try (Reader r = new FileReader(CRED_STORE)) {
                Type t = new com.google.gson.reflect.TypeToken<Map<String,String>>(){}.getType();
                Map<String,String> loaded = new Gson().fromJson(r, t);
                if (loaded != null) {
                    userCredentials.putAll(loaded);
                }
            } catch (IOException e) {
                System.err.println("Failed to load users.json: " + e.getMessage());
            }
        }
        // Ensure there's always at least one default account
        userCredentials.putIfAbsent("default", "default|asd");

        // Save back immediately in case defaultUser was just added
        saveCredentials();
    }

    private static synchronized void saveCredentials() {
        try (Writer w = new FileWriter(CRED_STORE)) {
            Gson gson = new Gson();
            gson.toJson(userCredentials, w);
        } catch (IOException e) {
            System.err.println("Failed to save users.json: " + e.getMessage());
        }
    }

    // store only host+port per worker
    public static final Map<Integer,String> workerHostsById =
            Collections.synchronizedMap(new HashMap<>());
    public static final Map<Integer,Integer> workerPortsById =
            Collections.synchronizedMap(new HashMap<>());

    private static int workerCount = 0;
    public static final Object workerAvailable = new Object();
    public static final List<Store> dynamicStores =
            Collections.synchronizedList(new ArrayList<>());
    public static final Set<String> dynamicRemoves =
            Collections.synchronizedSet(new HashSet<>());

    public static final Map<String,String> pendingReduceResults =
            Collections.synchronizedMap(new HashMap<>());
    public static final Object reduceLock = new Object();

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(MASTER_PORT)) {
            System.out.println("Master listening on " + MASTER_PORT);
            while (true) {
                Socket sock = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                sock.setSoTimeout(1000);

                String line = in.readLine();
                if ("REGISTER".equalsIgnoreCase(line)) {
                    try {
                        String data = in.readLine(); // Potential IOException or null return

                        if (data == null) {
                            if (out != null) {
                                out.println("ERROR:NO_DATA_FOR_REGISTER");
                            }
                            System.err.println("REGISTER: No data received after REGISTER command.");
                        } else {
                            String[] parts = data.split("\\|", -1); // Split by pipe, -1 preserves trailing empty strings
                            String user = null;
                            String pass = null;

                            if (parts.length == 2) { // Expected format: "username|password"
                                user = parts[0];
                                pass = parts[1];
                            } else if (parts.length >= 3) { // Expected format: "ignored_prefix|username|password" (or more parts, we only care about user/pass)
                                user = parts[1];
                                pass = parts[2];
                            } else {
                                // Invalid format (e.g., "username_only", "", or less than 2 parts)
                                if (out != null) {
                                    out.println("ERROR:INVALID_REGISTER_FORMAT");
                                }
                                System.err.println("REGISTER: Invalid data format (expected 2 or >=3 parts): " + data);
                            }

                            // Proceed only if user and pass were successfully parsed
                            if (user != null && pass != null) {
                                // Optional: Add validation for empty username if required
                                if (user.isEmpty()) {
                                    if (out != null) out.println("ERROR:USERNAME_CANNOT_BE_EMPTY");
                                    System.err.println("REGISTER: Username was empty. Data: " + data);
                                } else if (userCredentials.containsKey(user)) {
                                    if (out != null) {
                                        out.println("ERROR:USER_EXISTS");
                                    }
                                } else {
                                    userCredentials.put(user, pass);
                                    saveCredentials(); // Potential IOException or other errors during save
                                    if (out != null) {
                                        out.println("REGISTER_SUCCESS");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) { // Catches IOException, NullPointerException, ArrayIndexOutOfBoundsException, etc.
                        System.err.println("Error during REGISTER processing: " + e.getMessage());
                        // e.printStackTrace(); // Uncomment for detailed stack trace during debugging
                        if (out != null) {
                            out.println("ERROR:SERVER_ERROR_DURING_REGISTER");
                        }
                    } finally {
                        try {
                            if (sock != null && !sock.isClosed()) {
                                sock.close();
                            }
                        } catch (IOException ex) {
                            System.err.println("IOException while closing socket after REGISTER: " + ex.getMessage());
                        }
                    }
                    continue; // Continue to the next iteration of the server loop
                }

// --- handle LOGIN ---
                if ("LOGIN".equalsIgnoreCase(line)) {
                    try {
                        String data = in.readLine(); // Potential IOException or null return

                        if (data == null) {
                            if (out != null) {
                                out.println("ERROR:NO_DATA_FOR_LOGIN");
                            }
                            System.err.println("LOGIN: No data received after LOGIN command.");
                        } else {
                            String[] parts = data.split("\\|", -1); // Split by pipe, -1 preserves trailing empty strings
                            String user = null;
                            String pass = null;

                            if (parts.length == 2) { // Expected format: "username|password"
                                user = parts[0];
                                pass = parts[1];
                            } else if (parts.length >= 3) { // Expected format: "ignored_prefix|username|password" (or more)
                                user = parts[1];
                                pass = parts[2];
                            } else {
                                // Invalid format
                                if (out != null) {
                                    out.println("ERROR:INVALID_LOGIN_FORMAT");
                                }
                                System.err.println("LOGIN: Invalid data format (expected 2 or >=3 parts): " + data);
                            }

                            // Proceed only if user and pass were successfully parsed
                            if (user != null && pass != null) {
                                // Optional: Add validation for empty username if required for login
                                if (user.isEmpty()) {
                                    if (out != null) out.println("ERROR:USERNAME_CANNOT_BE_EMPTY_FOR_LOGIN");
                                    System.err.println("LOGIN: Username was empty. Data: " + data);
                                } else if (!userCredentials.containsKey(user) ||
                                        !userCredentials.get(user).equals(pass)) {
                                    if (out != null) {
                                        out.println("ERROR:INVALID_CREDENTIALS");
                                    }
                                } else {
                                    String token = UUID.randomUUID().toString();
                                    userSessions.put(token, user);
                                    if (out != null) {
                                        out.println("LOGIN_SUCCESS|" + token);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) { // Catches IOException, NullPointerException, ArrayIndexOutOfBoundsException, etc.
                        System.err.println("Error during LOGIN processing: " + e.getMessage());
                        // e.printStackTrace(); // Uncomment for detailed stack trace during debugging
                        if (out != null) {
                            out.println("ERROR:SERVER_ERROR_DURING_LOGIN");
                        }
                    } finally {
                        try {
                            if (sock != null && !sock.isClosed()) {
                                sock.close();
                            }
                        } catch (IOException ex) {
                            System.err.println("IOException while closing socket after LOGIN: " + ex.getMessage());
                        }
                    }
                    continue; // Continue to the next iteration of the server loop
                }
                if ("WORKER_HANDSHAKE".equals(line)) {
                    int wp = Integer.parseInt(in.readLine().trim());
                    int id;
                    synchronized (MasterServer.class) { id = workerCount++; }
                    out.println("WORKER_ASSIGN:" + id + ":" + workerCount);

                    // replay dynamic store adds
                    Gson gson = new Gson();
                    synchronized (dynamicStores) {
                        for (Store s : dynamicStores) {
                            String jobId = ActionForClients.generateJobId();
                            out.println("ADD_STORE(REPLAY)");
                            out.println(gson.toJson(s));
                            out.println(jobId);
                        }
                    }
                    synchronized (dynamicRemoves) {
                        for (String name : dynamicRemoves) {
                            String jobId = ActionForClients.generateJobId();
                            out.println("REMOVE_STORE(REPLAY)");
                            out.println(name);
                            out.println(jobId);
                        }
                    }

                    String host = sock.getInetAddress().getHostAddress();
                    workerHostsById.put(id, host);
                    workerPortsById.put(id, wp);
                    synchronized (workerAvailable){
                        workerAvailable.notifyAll();
                    }
                    sock.close();
                    broadcastReload();
                    System.out.printf("Worker %d @ %s:%d registered%n", id, host, wp);

                } else if (line != null && line.startsWith("WORKER_SHUTDOWN:")) {
                    int rem = Integer.parseInt(line.split(":")[1].trim());
                    synchronized (MasterServer.class) { workerCount--; }
                    workerHostsById.remove(rem);
                    workerPortsById.remove(rem);
                    synchronized (workerAvailable) {
                        shiftWorkerIdsDown(rem);
                        workerAvailable.notifyAll();
                        broadcastReload();
                    }
                    sock.close();

                } else if ("REDUCE_RESULT".equals(line)) {
                    String jobId = in.readLine();
                    String cmd   = in.readLine();
                    String agg   = in.readLine();
                    synchronized (reduceLock) {
                        pendingReduceResults.put(cmd + "|" + jobId, agg);
                        reduceLock.notifyAll();
                    }
                    out.println("ACK");
                    sock.close();

                } else {
                    new Thread(new ActionForClients(sock, line, in)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcastReload() {
        int total = workerHostsById.size();
        for (int id : workerHostsById.keySet()) {
            String h = workerHostsById.get(id);
            int    p = workerPortsById.get(id);
            try (Socket s = new Socket(h, p);
                 PrintWriter o = new PrintWriter(s.getOutputStream(), true)) {
                String jobId = ActionForClients.generateJobId();
                o.println("RELOAD");
                o.println(total);
                o.println(jobId);
            } catch (IOException ex) {
                System.err.println("Reload→" + id + " failed: " + ex.getMessage());
            }
        }
    }

    public static void broadcastToReplicas(List<Integer> ids, String msg) {
        for (int id : ids) {
            String h = workerHostsById.get(id);
            int    p = workerPortsById.get(id);
            try (Socket s = new Socket(h, p);
                 PrintWriter o = new PrintWriter(s.getOutputStream(), true)) {
                o.println(msg);
            } catch (IOException ex) {
                System.err.println("Replica→" + id + " failed: " + ex.getMessage());
            }
        }
    }

    private static void shiftWorkerIdsDown(int removedId) {
        Map<Integer,String> newH = new HashMap<>();
        Map<Integer,Integer> newP = new HashMap<>();
        for (int old : new ArrayList<>(workerHostsById.keySet())) {
            String h = workerHostsById.remove(old);
            int    p = workerPortsById.remove(old);
            if (old < removedId) {
                newH.put(old, h); newP.put(old, p);
            } else if (old > removedId) {
                newH.put(old-1, h); newP.put(old-1, p);
            }
        }
        workerHostsById.putAll(newH);
        workerPortsById.putAll(newP);
        for (var e : newH.entrySet()) {
            int id = e.getKey();
            String h = e.getValue();
            int    p = newP.get(id);
            try (Socket s = new Socket(h, p);
                 PrintWriter o = new PrintWriter(s.getOutputStream(), true)) {
                String jobId = ActionForClients.generateJobId();
                o.println("DECREMENT_ID");
                o.println(id + ":" + workerHostsById.size());
                o.println(jobId);
            } catch (IOException ex) {
                System.err.println("Notify→" + id + " failed: " + ex.getMessage());
            }
        }
    }
}
