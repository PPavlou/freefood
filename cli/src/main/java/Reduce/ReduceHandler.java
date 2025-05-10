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
    private final Socket socket;
    private static final Map<String, AggregationJob> jobs = new HashMap<>();

    // Master connection info
    private static final String MASTER_HOST = "172.20.10.3";
    private static final int MASTER_PORT = 12345;

    public ReduceHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                Socket s = socket;
                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter writer = new PrintWriter(s.getOutputStream(), true)
        ) {
            String jobId = reader.readLine();
            if (jobId == null) return;
            String command = reader.readLine();
            if (command == null) return;
            int expectedCount = Integer.parseInt(reader.readLine().trim());
            String mappingJson = reader.readLine();

            System.out.println("Reduce received job=" + jobId + ", cmd=" + command);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<MapReduceFramework.Pair<String, String>>>(){}.getType();
            List<MapReduceFramework.Pair<String, String>> partialMapping =
                    gson.fromJson(mappingJson, listType);

            AggregationJob job;
            synchronized (jobs) {
                job = jobs.computeIfAbsent(jobId,
                        id -> new AggregationJob(jobId, command, expectedCount));
            }

            synchronized (job) {
                job.partials.add(partialMapping);
                if (!job.isCompleted && job.partials.size() >= job.expectedCount) {
                    Map<String, String> reduced = new HashMap<>();
                    for (var list : job.partials) {
                        for (var pair : list) {
                            reduced.merge(pair.getKey(), pair.getValue(), (a, b) -> a + ", " + b);
                        }
                    }
                    job.finalResult = gson.toJson(reduced);
                    job.isCompleted = true;
                    job.notifyAll();

                    System.out.println("Aggregated job=" + jobId + ", result=" + job.finalResult);
                    sendAggregatedResultToMaster(jobId, command, job.finalResult);
                } else {
                    while (!job.isCompleted) {
                        try {
                            job.wait();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                writer.println("ACK");
                job.responseCount++;
            }

            synchronized (jobs) {
                if (job.responseCount >= job.expectedCount) {
                    jobs.remove(jobId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendAggregatedResultToMaster(String jobId, String command, String result) {
        try (Socket master = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter mw = new PrintWriter(master.getOutputStream(), true)) {
            mw.println("REDUCE_RESULT");
            mw.println(jobId);
            mw.println(command);
            mw.println(result);
            System.out.println("Sent REDUCE_RESULT for job=" + jobId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class AggregationJob {
        final String jobId;
        final String command;
        final int expectedCount;
        final List<List<MapReduceFramework.Pair<String, String>>> partials = new ArrayList<>();
        boolean isCompleted = false;
        String finalResult;
        int responseCount = 0;

        AggregationJob(String jobId, String command, int expectedCount) {
            this.jobId = jobId;
            this.command = command;
            this.expectedCount = expectedCount;
        }
    }
}
