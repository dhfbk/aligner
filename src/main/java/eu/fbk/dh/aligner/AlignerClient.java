package eu.fbk.dh.aligner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


public class AlignerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlignerClient.class);
    private BufferedReader in;
    private PrintWriter out;

    public AlignerClient(String address, Integer port) throws IOException {
        Socket socket = new Socket(address, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    public String runQuery(String query) throws IOException {
        out.println(query);
        String response;
        response = in.readLine();
        if (response == null || response.equals("")) {
            throw new IOException("Response is empty");
        }

        return response;
    }

    private BufferedReader getIn() {
        return in;
    }

    private PrintWriter getOut() {
        return out;
    }

    public static void main(String[] args) throws Exception {
        AlignerClient client = new AlignerClient("dh-server.fbk.eu", 9010);
        String response = client.runQuery("Questa è la mia vita" + Aligner.SEPARATOR + "This is my life");
        System.out.println(response);
    }

}
