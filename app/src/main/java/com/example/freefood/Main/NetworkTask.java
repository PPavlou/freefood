package com.example.freefood.Main;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles all socket I/O, now injecting the session token if present.
 * Also provides a blocking sendCommand(...) helper for AuthManager.
 */
public class NetworkTask extends AsyncTask<String, Void, String> {

    public interface NetworkCallback {
        void onNetworkTaskComplete(String result);
    }

    private final String serverHost;
    private final int serverPort;
    private final NetworkCallback callback;
    private static String sessionToken = null;

    public NetworkTask(String serverHost, int serverPort, NetworkCallback callback) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.callback   = callback;
    }

    /** Static methods for AuthManager or logout flows **/
    public static void setSessionToken(String token) {
        sessionToken = token;
    }

    public static void clearSessionToken() {
        sessionToken = null;
    }

    /**
     * Blocking, synchronous sendCommand method.
     * Can be called from background threads.
     */
    public String sendCommand(String command, String data) {

    /*  ---- TOKEN GUARD ----
        Do NOT prepend the session-token for commands that don’t expect it.
     */
        boolean needsToken = sessionToken != null
                && !command.equals("PURCHASE_PRODUCT")
                && !command.equals("REVIEW")
                && !command.equals("SEARCH")
                && !command.equals("STORE_DETAILS")
                && !command.equals("GET_STOCK")
                && !command.equals("GET_LOGO");

        String payload = needsToken
                ? sessionToken + "|" + data
                : data;

        try (Socket socket = new Socket(serverHost, serverPort);
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            writer.println(command);
            writer.println(payload);
            return reader.readLine();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /** AsyncTask plumbing (unchanged) **/
    @Override
    protected String doInBackground(String... params) {
        if (params.length < 2) {
            return "Error: Insufficient parameters";
        }
        return sendCommand(params[0], params[1]);
    }

    @Override
    protected void onPostExecute(String result) {
        if (callback != null) {
            callback.onNetworkTaskComplete(result);
        }
    }
}
