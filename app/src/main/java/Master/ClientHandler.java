package Master;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.DistributedMapReduceJob;
import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private List<Socket> workerSockets;
    private BufferedReader initialReader;
    private String firstLine;

    public ClientHandler(Socket clientSocket, List<Socket> workerSockets, String firstLine, BufferedReader initialReader) {
        this.clientSocket = clientSocket;
        this.workerSockets = workerSockets;
        this.firstLine = firstLine;
        this.initialReader = initialReader;
    }

    @Override
    public void run() {
        try (BufferedReader reader = initialReader;
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String command = firstLine;
            String data = reader.readLine();
            System.out.println("Master received command: " + command);
            System.out.println("Master received data: " + data);

            List<String> workerResponses = forwardToAllWorkers(command, data);
            String finalResponse;
            Gson gson = new Gson();

            // For commands that are externally reduced (client: SEARCH, REVIEW, AGGREGATE_SALES_BY_PRODUCT_NAME; manager: LIST_STORES, DELETED_PRODUCTS),
            // merge the reduced results (each response is a JSON object representing a Map<String, String>).
            if (command.equalsIgnoreCase("SEARCH") ||
                    command.equalsIgnoreCase("REVIEW") ||
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME") ||
                    command.equalsIgnoreCase("LIST_STORES") ||
                    command.equalsIgnoreCase("DELETED_PRODUCTS")) {

                Map<String, String> combined = new HashMap<>();
                for (String response : workerResponses) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> partial = gson.fromJson(response, type);
                    for (Map.Entry<String, String> entry : partial.entrySet()) {
                        combined.merge(entry.getKey(), entry.getValue(), (v1, v2) -> v1 + "\n" + v2);
                    }
                }
                finalResponse = gson.toJson(combined);
            } else {
                // For manager commands that do not require external reduction, use the local DistributedMapReduceJob.
                ManagerCommandMapperReducer.CommandReducer reducer =
                        new ManagerCommandMapperReducer.CommandReducer(command);
                DistributedMapReduceJob<String, String, String> job =
                        new DistributedMapReduceJob<>(workerResponses, reducer);
                finalResponse = gson.toJson(job.execute());
            }
            writer.println(finalResponse);
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { }
        }
    }

    private List<String> forwardToAllWorkers(String command, String data) {
        List<String> responses = new ArrayList<>();
        synchronized(workerSockets) {
            for (Socket workerSocket : workerSockets) {
                try {
                    PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                    out.println(command);
                    out.println(data);
                    String resp = in.readLine();
                    responses.add(resp);
                } catch (IOException e) {
                    responses.add("Error with worker: " + e.getMessage());
                }
            }
        }
        return responses;
    }
}
