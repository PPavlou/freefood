package workers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class WorkerClientHandler implements Runnable {
    private Socket socket;
    private Worker1 worker;

    public WorkerClientHandler(Socket socket, Worker1 worker) {
        this.socket = socket;
        this.worker = worker;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // Read command and data from the socket.
            String command = in.readLine();
            String data = in.readLine();
            System.out.println("Worker received command: " + command);
            System.out.println("Worker received data: " + data);

            // Process the command using the worker's processCommand method.
            String response = worker.processCommand(command, data);
            out.println(response);
        } catch (IOException e) {
            System.err.println("WorkerClientHandler error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore errors during socket close.
            }
        }
    }
}
