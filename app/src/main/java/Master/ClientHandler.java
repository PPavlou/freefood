package Master;

import com.google.gson.Gson;
import mapreduce.ClientCommandMapperReducer;
import mapreduce.ManagerCommandMapperReducer;
import mapreduce.DistributedMapReduceJob;
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

            List<String> workerResponses = forwardToAllWorkers(command, data);
            String finalResponse;
            Gson gson = new Gson();

            // Choose reducer based on client or manager command.
            if (command.equalsIgnoreCase("SEARCH") ||
                    command.equalsIgnoreCase("REVIEW") ||
                    command.equalsIgnoreCase("AGGREGATE_SALES_BY_PRODUCT_NAME")) {
                ClientCommandMapperReducer.ClientCommandReducer reducer =
                        new ClientCommandMapperReducer.ClientCommandReducer(command);
                DistributedMapReduceJob<String, String, String> job =
                        new DistributedMapReduceJob<>(workerResponses, reducer);
                finalResponse = gson.toJson(job.execute());
            } else {
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
