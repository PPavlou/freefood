package com.example.freefood;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * An AsyncTask to handle network operations off the main thread.
 * This is a better approach for Android than running network operations on the main thread.
 */
public class NetworkTask extends AsyncTask<String, Void, String> {

    private final String serverHost;
    private final int serverPort;
    private final NetworkCallback callback;

    /**
     * Interface to handle the result of the network operation.
     */
    public interface NetworkCallback {
        void onNetworkTaskComplete(String result);
    }

    /**
     * Creates a new NetworkTask.
     *
     * @param serverHost the server hostname
     * @param serverPort the server port
     * @param callback the callback to handle the result
     */
    public NetworkTask(String serverHost, int serverPort, NetworkCallback callback) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.callback = callback;
    }

    @Override
    protected String doInBackground(String... params) {
        if (params.length < 2) {
            return "Error: Insufficient parameters";
        }

        String command = params[0];
        String data = params[1];
        String response = "";

        try (Socket socket = new Socket(serverHost, serverPort);
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            // Send the command and data on separate lines.
            writer.println(command);
            writer.println(data);

            // Read and return the response.
            response = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }

        return response;
    }

    @Override
    protected void onPostExecute(String result) {
        if (callback != null) {
            callback.onNetworkTaskComplete(result);
        }
    }
}