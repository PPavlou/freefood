package Reduce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.MapReduceFramework;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReduceHandler implements Runnable {
    private Socket socket;

    // Global plain HashMap to store aggregation jobs keyed by command.
    private static final Map<String, AggregationJob> jobs = new HashMap<>();

    // Master connection info (master already listens on port 12345).
    private static final String MASTER_HOST = "192.168.1.14"; // adjust as needed
    private static final int MASTER_PORT = 12345;

    public ReduceHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // 1st line: the command (e.g., "LIST_STORES")
            String command = reader.readLine();
            if (command == null) return;

            // 2nd line: expected number of mapping outputs (number of workers)
            String expectedCountStr = reader.readLine();
            int expectedCount = Integer.parseInt(expectedCountStr.trim());

            // 3rd line: the mapping result JSON from this worker
            String mappingJson = reader.readLine();

            System.out.println("Reduce server received command: " + command);
            System.out.println("Mapping result JSON: " + mappingJson);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>() {}.getType();
            List<MapReduceFramework.Pair<String, String>> partialMapping = gson.fromJson(mappingJson, listType);

            AggregationJob job;
            synchronized (jobs) {
                job = jobs.get(command);
                if (job == null) {
                    job = new AggregationJob(command, expectedCount);
                    jobs.put(command, job);
                }
            }

            synchronized (job) {
                // Add this worker's entire partial mapping to the job.
                job.partials.add(partialMapping);
                // If all expected partials have arrived and we haven't aggregated yet…
                if (!job.isCompleted && job.partials.size() >= job.expectedCount) {
                    Map<String, String> reducedResults = new HashMap<>();
                    for (List<MapReduceFramework.Pair<String, String>> partialList : job.partials) {
                        for (MapReduceFramework.Pair<String, String> pair : partialList) {
                            reducedResults.merge(pair.getKey(), pair.getValue(), (v1, v2) -> v1 + ", " + v2);
                        }
                    }
                    job.finalResult = gson.toJson(reducedResults);
                    job.isCompleted = true;
                    job.notifyAll();
                    System.out.println("Reduced result computed for command " + command + ": " + job.finalResult);

                    // Send the aggregated result directly to the master.
                    sendAggregatedResultToMaster(command, job.finalResult);
                    job.sentToMaster = true;
                } else {
                    // Otherwise, wait until aggregation is complete.
                    while (!job.isCompleted) {
                        try {
                            job.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                // Reply an acknowledgment to the worker.
                writer.println("ACK");
                job.responseCount++;
            }

            // Once responses have been sent to all expected workers, remove the job.
            synchronized (jobs) {
                if (job.responseCount >= job.expectedCount) {
                    jobs.remove(command);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) { reader.close(); }
                if (writer != null) { writer.close(); }
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Connects to the master on port 12345 and sends the aggregated result.
    private void sendAggregatedResultToMaster(String command, String aggregatedResult) {
        try (Socket masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter masterWriter = new PrintWriter(masterSocket.getOutputStream(), true)) {
            // We use a special header "REDUCE_RESULT" to distinguish reduce server messages.
            masterWriter.println("REDUCE_RESULT");
            masterWriter.println(command);
            masterWriter.println(aggregatedResult);
            System.out.println("Aggregated result sent to master: " + aggregatedResult);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Inner class representing an aggregation job.
    private static class AggregationJob {
        public final String command;
        public final int expectedCount;
        // List of partial mapping outputs (each partial is a List of key-value pairs).
        public final List<List<MapReduceFramework.Pair<String, String>>> partials;
        public boolean isCompleted = false;
        public String finalResult = null;
        public boolean sentToMaster = false;
        public int responseCount = 0;

        public AggregationJob(String command, int expectedCount) {
            this.command = command;
            this.expectedCount = expectedCount;
            this.partials = new ArrayList<>();
        }
    }
}
