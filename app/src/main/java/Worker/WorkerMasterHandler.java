package Worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class WorkerMasterHandler implements Runnable {
    private Socket socket;
    private Worker worker;

    public WorkerMasterHandler(Socket socket, Worker worker) {
        this.socket = socket;
        this.worker = worker;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String command = in.readLine();
            String data = in.readLine();
            System.out.println("Worker received command: " + command);
            System.out.println("Worker received data: " + data);
            String response = worker.processCommand(command, data);
            out.println(response);
        } catch (IOException e) {
            System.err.println("WorkerMasterHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore errors on close
            }
        }
    }
}
