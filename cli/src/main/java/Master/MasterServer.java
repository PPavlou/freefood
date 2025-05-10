// MasterServer.java
package Master;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import com.google.gson.Gson;
import model.Store;

public class MasterServer {
    private static final int MASTER_PORT = 12345;

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
