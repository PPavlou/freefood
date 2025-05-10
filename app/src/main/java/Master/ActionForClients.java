package Master;

import java.io.*;
import java.net.Socket;
import java.util.*;
import com.google.gson.Gson;
import model.Store;

public class ActionForClients implements Runnable {
    private final Socket clientSocket;
    private final BufferedReader initialReader;
    private final String firstLine;

    public ActionForClients(Socket clientSocket,
                            String firstLine,
                            BufferedReader initialReader) {
        this.clientSocket  = clientSocket;
        this.firstLine     = firstLine;
        this.initialReader = initialReader;
    }

    public static String generateJobId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        try (BufferedReader in = initialReader;
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String cmd  = firstLine;
            String data = in.readLine();
            String jobId = generateJobId();

            List<String> responses = forwardToWorkers(cmd, data, jobId);
            String finalResponse;
            Gson gson = new Gson();

            if ("ADD_STORE".equalsIgnoreCase(cmd) || "REMOVE_STORE".equalsIgnoreCase(cmd)) {
                finalResponse = responses.isEmpty() ? "[]" : responses.get(0);

            } else {
                Set<String> reduces = Set.of(
                        "SEARCH", "AGGREGATE_SALES_BY_PRODUCT_NAME",
                        "LIST_STORES", "DELETED_PRODUCTS"
                );
                if (reduces.contains(cmd.toUpperCase())) {
                    String key = cmd + "|" + jobId;
                    synchronized (MasterServer.reduceLock) {
                        while (!MasterServer.pendingReduceResults.containsKey(key)) {
                            try {
                                MasterServer.reduceLock.wait(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                out.println("{\"error\":\"Interrupted while waiting for reduce result.\"}");
                                return;
                            }
                        }
                        finalResponse = MasterServer.pendingReduceResults.remove(key);
                    }
                } else {
                    finalResponse = gson.toJson(responses);
                }
            }

            out.println(finalResponse);

            // On ADD/REMOVE store, update dynamics and reload
            if ("ADD_STORE".equalsIgnoreCase(cmd) || "REMOVE_STORE".equalsIgnoreCase(cmd)) {
                if ("ADD_STORE".equalsIgnoreCase(cmd)) {
                    Store s = gson.fromJson(data, Store.class);
                    MasterServer.dynamicStores.add(s);
                } else {
                    MasterServer.dynamicStores.removeIf(s -> s.getStoreName().equals(data.trim()));
                    MasterServer.dynamicRemoves.add(data.trim());
                }
                MasterServer.broadcastReload();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private List<String> forwardToWorkers(String cmd, String data, String jobId) {
        List<String> res = new ArrayList<>();
        Set<String> directed = Set.of(
                "ADD_PRODUCT","REMOVE_PRODUCT",
                "UPDATE_PRODUCT_AMOUNT","INCREMENT_PRODUCT_AMOUNT",
                "DECREMENT_PRODUCT_AMOUNT","PURCHASE_PRODUCT","REVIEW"
        );

        if (directed.contains(cmd.toUpperCase())) {
            String store = extractStoreName(cmd, data);
            synchronized (MasterServer.workerAvailable) {
                while (MasterServer.workerHostsById.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        res.add("{\"error\":\"Interrupted while waiting for a worker.\"}");
                        return res;
                    }
                }
            }
            int wc = MasterServer.workerHostsById.size();
            int primary = Math.abs(store.hashCode()) % wc;
            String r = sendToWorker(primary, cmd, data, jobId);
            res.add(r);
            if (wc > 1 && r.startsWith("{\"error\"")) {
                List<Integer> reps = new ArrayList<>();
                for (int i=1; i<2; i++) reps.add((primary+i)%wc);
                MasterServer.broadcastToReplicas(reps, cmd + "|" + data + "|" + jobId);
            }

        } else {
            synchronized (MasterServer.workerAvailable) {
                while (MasterServer.workerHostsById.isEmpty()) {
                    try {
                        MasterServer.workerAvailable.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        res.add("{\"error\":\"Interrupted while waiting for workers.\"}");
                        return res;
                    }
                }
            }
            for (var id : MasterServer.workerHostsById.keySet()) {
                res.add(sendToWorker(id, cmd, data, jobId));
            }
        }
        return res;
    }

    private String sendToWorker(int id, String cmd, String data, String jobId) {
        String h = MasterServer.workerHostsById.get(id);
        int    p = MasterServer.workerPortsById.get(id);
        try (Socket s = new Socket(h, p);
             PrintWriter o = new PrintWriter(s.getOutputStream(), true);
             BufferedReader i = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            o.println(cmd);
            o.println(data);
            o.println(jobId);
            String line;
            while ((line = i.readLine()) != null) {
                if (line.startsWith("CMD_RESPONSE:")) {
                    return line.substring("CMD_RESPONSE:".length());
                }
            }
        } catch (IOException e) {
            return "{\"error\":\"Worker comms failed\"}";
        }
        return "{\"error\":\"No response\"}";
    }

    private String extractStoreName(String cmd, String data) {
        if ("ADD_STORE".equalsIgnoreCase(cmd)) {
            try {
                return new Gson().fromJson(data, Store.class).getStoreName();
            } catch (Exception e) {
                return null;
            }
        }
        return data.split("\\|")[0].trim();
    }
}
