
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

    // Use a plain HashMap to store aggregation jobs (keyed by the command)
    private static final Map<String, AggregationJob> jobs = new HashMap<>();

    public ReduceHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // First line: the command (e.g. LIST_STORES)
            String command = reader.readLine();
            if (command == null) return;

            // Second line: expected number of mapping outputs (from the worker)
            String expectedCountStr = reader.readLine();
            int expectedCount = Integer.parseInt(expectedCountStr.trim());

            // Third line: the mapping result JSON sent by the worker
            String mappingJson = reader.readLine();
            System.out.println("Reduce server received command: " + command);
            System.out.println("Mapping result JSON: " + mappingJson);

            Gson gson = new Gson();
            Type pairListType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>() {}.getType();
            List<MapReduceFramework.Pair<String, String>> pairs = gson.fromJson(mappingJson, pairListType);

            // Use a synchronized block on the jobs map to obtain (or create) the job for this command.
            AggregationJob job;
            synchronized (jobs) {
                job = jobs.get(command);
                if (job == null) {
                    job = new AggregationJob(command, expectedCount);
                    jobs.put(command, job);
                }
            }

            synchronized (job) {
                // Add the worker's partial mapping outputs to the job.
                job.partials.addAll(pairs);

                // If we have not computed the final result and the number of received partials is at least the expected count,
                // then perform the aggregation (the reduction step).
                if (!job.isCompleted && job.receivedCount() >= job.expectedCount) {
                    Map<String, String> reducedResults = new HashMap<>();
                    for (MapReduceFramework.Pair<String, String> pair : job.partials) {
                        // Merge by concatenating values with a comma.
                        reducedResults.merge(pair.getKey(), pair.getValue(), (v1, v2) -> v1 + ", " + v2);
                    }
                    job.finalResult = gson.toJson(reducedResults);
                    job.isCompleted = true;
                    job.notifyAll();
                    System.out.println("Reduced result computed for command " + command + ": " + job.finalResult);
                } else {
                    // Otherwise wait until the job becomes complete.
                    while (!job.isCompleted) {
                        try {
                            job.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                // Reply back with the same final aggregated result.
                writer.println(job.finalResult);
                System.out.println("Reduced result sent: " + job.finalResult);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) { }
        }
    }

    // Inner class representing an aggregation job for a given command.
    private static class AggregationJob {
        public final String command;
        public final int expectedCount;
        public final List<MapReduceFramework.Pair<String, String>> partials;
        public boolean isCompleted = false;
        public String finalResult = null;

        public AggregationJob(String command, int expectedCount) {
            this.command = command;
            this.expectedCount = expectedCount;
            this.partials = new ArrayList<>();
        }

        public int receivedCount() {
            return partials.size();
        }
    }
}
