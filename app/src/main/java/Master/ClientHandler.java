package Master;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import Worker.WorkerInfo;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private List<WorkerInfo> workerList;
    private Socket workerSocket;
    private BufferedReader initialReader;
    private String firstLine;

    public ClientHandler(Socket clientSocket, Socket workerSocket, String firstLine, BufferedReader initialReader) {
        this.clientSocket = clientSocket;
        this.workerSocket = workerSocket;
        this.firstLine = firstLine;
        this.initialReader = initialReader;
    }

    @Override
    public void run() {
        try (
                // Use the already available initialReader for the manager connection.
                BufferedReader reader = initialReader;
                OutputStream outStream = clientSocket.getOutputStream();
                PrintWriter writer = new PrintWriter(outStream, true)
        ) {
            // Use the firstLine as the command
            String command = firstLine;
            String data = reader.readLine();
            System.out.println("Master received command: " + command);
            System.out.println("Master received data: " + data);

            // Forward the command using the persistent worker connection.
            String response = forwardToWorker(command, data);
            writer.println(response);
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch(IOException e) {}
        }
    }

    private WorkerInfo chooseWorker(String command, String data) {
        // For LIST_STORES or unknown commands, simply use the first worker.
        if (command.equals("LIST_STORES")) {
            return workerList.get(0);
        } else if (command.equals("ADD_STORE") || command.equals("PURCHASE_PRODUCT") ||
                command.equals("ADD_PRODUCT") || command.equals("REMOVE_STORE") ||
                command.equals("REMOVE_PRODUCT")) {
            // Extract store name from data.
            String storeName = extractStoreName(command, data);
            if (storeName == null) {
                return workerList.get(0);
            }
            int hash = Math.abs(storeName.hashCode());
            int index = hash % workerList.size();
            return workerList.get(index);
        }
        return workerList.get(0);
    }

    private String extractStoreName(String command, String data) {
        if (command.equals("ADD_STORE")) {
            // Data is JSON; here we simply search for the "StoreName" value.
            int idx = data.indexOf("\"StoreName\"");
            if (idx != -1) {
                int colon = data.indexOf(":", idx);
                int startQuote = data.indexOf("\"", colon);
                int endQuote = data.indexOf("\"", startQuote + 1);
                if (startQuote != -1 && endQuote != -1) {
                    return data.substring(startQuote + 1, endQuote);
                }
            }
        } else {
            // For other commands, assume the format "storeName|..."
            String[] parts = data.split("\\|");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        return null;
    }

    private String forwardToWorker(String command, String data) {
        if (workerSocket == null) {
            return "No worker available.";
        }
        String response = "";
        try {
            PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
            out.println(command);
            out.println(data);
            response = in.readLine();
        } catch (IOException e) {
            response = "Error communicating with worker: " + e.getMessage();
            System.err.println(response);
        }
        return response;
    }
}
