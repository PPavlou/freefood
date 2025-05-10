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


/**
 * Handles reduce operations by aggregating partial mapping results
 * from worker nodes and forwarding the aggregated result to the Master server.
 */
public class ReduceHandler implements Runnable {
    /** Socket connected to the worker node. */
    private Socket socket;

    /** Jobs currently in progress, keyed by job ID. */
    private static final Map<String, AggregationJob> jobs = new HashMap<>();

    // Master connection info (master already listens on port 12345).
    private static final String MASTER_HOST = "localhost"; // adjust as needed
    private static final int MASTER_PORT = 12345;

    /**
     * Constructs a ReduceHandler for the given worker socket.
     *
     * @param socket the socket connected to a worker
     */
    public ReduceHandler(Socket socket) {
        this.socket = socket;
    }

    /**
     * Processes a single worker's partial mapping output.
     * Reads the job ID, the command name, the number of expected partials, and the JSON mapping output.
     * Collects partials until all are received, performs aggregation, sends the result to
     * the Master, acknowledges the worker, and removes the job when complete.
     */
    @Override
    public void run() {
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // 1st line: the job ID
            String jobId = reader.readLine();
            if (jobId == null) return;

            // 2nd line: the command (e.g., "LIST_STORES")
            String command = reader.readLine();
            if (command == null) return;

            // 3rd line: expected number of mapping outputs (number of workers)
            String expectedCountStr = reader.readLine();
            int expectedCount = Integer.parseInt(expectedCountStr.trim());

            // 4th line: the mapping result JSON from this worker
            String mappingJson = reader.readLine();

            System.out.println("Reduce server received job ID: " + jobId);
            System.out.println("Command: " + command);
            System.out.println("Mapping result JSON: " + mappingJson);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>() {}.getType();
            List<MapReduceFramework.Pair<String, String>> partialMapping = gson.fromJson(mappingJson, listType);

            AggregationJob job;
            synchronized (jobs) {
                job = jobs.get(jobId);
                if (job == null) {
                    job = new AggregationJob(jobId, command, expectedCount);
                    jobs.put(jobId, job);
                }
            }

            synchronized (job) {
                // Add this worker's entire partial mapping to the job.
                job.partials.add(partialMapping);
                // If all expected partials have arrived and we haven't aggregated yet
                if (!job.isCompleted && job.partials.size() >= job.expectedCount) {
                    Map<String, String> reducedResults = new HashMap<>();
                    for (List<MapReduceFramework.Pair<String, String>> partialList : job.partials) {
                        for (MapReduceFramework.Pair<String, String> pair : partialList) {
                            reducedResults.merge(
                                    pair.getKey(),
                                    pair.getValue(),
                                    (v1, v2) -> v1 + ", " + v2
                            );
                        }
                    }
                    job.finalResult = gson.toJson(reducedResults);
                    job.isCompleted = true;
                    job.notifyAll();
                    System.out.println("Reduced result computed for job " + jobId + " (command: " + command + "): " + job.finalResult);

                    // Send the aggregated result directly to the master.
                    sendAggregatedResultToMaster(jobId, command, job.finalResult);
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
                    jobs.remove(jobId);
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

    /**
     * Sends the aggregated reduce result to the Master server.
     *
     * @param jobId            the ID of the job for which results were aggregated
     * @param command          the command for which results were aggregated
     * @param aggregatedResult the JSON string of the aggregated results
     */
    private void sendAggregatedResultToMaster(String jobId, String command, String aggregatedResult) {
        try (Socket masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter masterWriter = new PrintWriter(masterSocket.getOutputStream(), true)) {
            // We use a special header "REDUCE_RESULT" to distinguish reduce server messages.
            masterWriter.println("REDUCE_RESULT");
            masterWriter.println(jobId);
            masterWriter.println(command);
            masterWriter.println(aggregatedResult);
            System.out.println("Aggregated result sent to master for job " + jobId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Represents an aggregation job for a specific command.
     * Collects partial mapping outputs from multiple workers,
     * aggregates them into a final result, and tracks job state.
     */
    private static class AggregationJob {
        /** Unique ID for this job. */
        public final String jobId;
        /** Command associated with this job. */
        public final String command;
        /** Number of partial results expected. */
        public final int expectedCount;
        /** List of partial outputs collected so far. */
        public final List<List<MapReduceFramework.Pair<String, String>>> partials;
        /** Indicates whether aggregation has completed. */
        public boolean isCompleted = false;
        /** JSON string of the aggregated final result. */
        public String finalResult = null;
        /** Indicates whether the result was sent to the Master. */
        public boolean sentToMaster = false;
        /** Count of worker acknowledgments processed. */
        public int responseCount = 0;

        /**
         * Constructs a new AggregationJob.
         *
         * @param jobId         the unique ID for this job
         * @param command       the command for aggregation
         * @param expectedCount how many partial results to expect
         */
        public AggregationJob(String jobId, String command, int expectedCount) {
            this.jobId = jobId;
            this.command = command;
            this.expectedCount = expectedCount;
            this.partials = new ArrayList<>();
        }
    }
}