package Master;

import com.google.gson.Gson;
import mapreduce.DistributedMapReduceJob;
import mapreduce.SearchReducer; // Ensure that SearchReducer is in package 'mapreduce'
import model.Store;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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

            String finalResponse;
            if ("SEARCH".equalsIgnoreCase(command)) {
                // Forward the SEARCH command to all workers.
                List<String> workerResponses = forwardToAllWorkers(command, data);
                // Use DistributedMapReduceJob with our SearchReducer.
                // Note: SearchReducer implements Reducer<String, Store, List<Store>>
                DistributedMapReduceJob<String, Store, List<Store>> job =
                        new DistributedMapReduceJob<>(workerResponses, new SearchReducer());
                Gson gson = new Gson();
                finalResponse = gson.toJson(job.execute());
            } else {
                // For non-search commands, simply aggregate the responses.
                List<String> responses = forwardToAllWorkers(command, data);
                StringBuilder sb = new StringBuilder();
                for (String resp : responses) {
                    sb.append(resp).append("\n");
                }
                finalResponse = sb.toString();
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
