package eu.fbk.dh.aligner;

import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.PropertiesUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: alessio
 * Date: 08/08/14
 * Time: 15:43
 * To change this template use File | Settings | File Templates.
 */

public class Aligner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Aligner.class);
    public static final Integer DEFAULT_PORT = 8012;
    public static final String SEPARATOR = "_#_";

    private OutputStream stdin = null;
    private BufferedReader brCleanUp;
    private static int MAX_NUM_OF_RESTARTS = 5;
    private static int SLEEP_TIME = 10000;

    private int numOfRestarts = 0;
    private int maxNumOfRestarts;
    private int sleepTime;
    private String baseDir;

    public static void main(String[] args) {

        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./tint-server")
                    .withHeader("Run the eu.fbk.dh.aligner.Aligner Server")
                    .withOption("c", "config", "Configuration file", "FILE", CommandLine.Type.FILE_EXISTING, true,
                            false, true)
                    .withOption("p", "port", String.format("Host port (default %d)", DEFAULT_PORT), "NUM",
                            CommandLine.Type.INTEGER, true, false, false)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            Integer port = cmd.getOptionValue("port", Integer.class, DEFAULT_PORT);
            File configFile = cmd.getOptionValue("config", File.class);

            Properties properties = new Properties();

            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            properties.load(reader);
            reader.close();

            Aligner aligner = new Aligner(properties, true);

            LOGGER.info("Starting listener on port: " + port);
            ServerSocket listener = new ServerSocket(port);
            try {
                while (true) {
                    new AligherThread(listener.accept(), aligner).start();
                }
            } finally {
                LOGGER.info("Closing listener");
                listener.close();
            }

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }

    private static class AligherThread extends Thread {
        private Socket socket;
        private Aligner aligner;

        public AligherThread(Socket socket, Aligner aligner) {
            this.socket = socket;
            this.aligner = aligner;
            LOGGER.info("Incoming connection");
        }

        public void run() {
            try {

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String input = in.readLine();
                    if (input == null || input.equals(".")) {
                        break;
                    }
                    try {
                        input = input.replaceAll("[\n\r]", " ");
                        String output = "null";

                        int num = StringUtils.countMatches(input, SEPARATOR);
                        if (num == 1) {
                            output = aligner.run(input);
                        }

                        out.println(output.trim());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public Aligner(Properties properties) throws Exception {
        this(properties, false);
    }

    public Aligner(Properties properties, boolean sleep) throws Exception {
        baseDir = properties.getProperty("folder");
        maxNumOfRestarts = PropertiesUtils.getInteger(properties.getProperty("max_num_of_restarts"), MAX_NUM_OF_RESTARTS);
        sleepTime = PropertiesUtils.getInteger(properties.getProperty("sleep_time"), SLEEP_TIME);

        if (!baseDir.endsWith(File.separator)) {
            baseDir += File.separator;
        }

        init(sleep);
    }

    private void init() throws IOException, InterruptedException {
        init(false);
    }

    private void init(boolean sleep) throws IOException, InterruptedException {

        File inputFolder = new File(baseDir + "input");
        if (inputFolder.exists() && inputFolder.isDirectory()) {
            for (File file : inputFolder.listFiles()) {
                file.delete();
            }
        }
        File tmpFolder = new File(baseDir + "tmp");
        if (tmpFolder.exists() && tmpFolder.isDirectory()) {
            for (File file : tmpFolder.listFiles()) {
                file.delete();
            }
        }

        String[] command = {"bin/fa_align", "-m", "model.it-en/", "-s", "it", "-t", "en", "-i", "input/", "-a", "1", "-o", "tmp/"};

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(baseDir));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = pb.start();

        stdin = process.getOutputStream();
        InputStream stdout = process.getInputStream();
        brCleanUp = new BufferedReader(new InputStreamReader(stdout));

        LOGGER.debug("Sleeping...");
        if (sleep) {
            Thread.sleep(sleepTime);
        }
    }

    synchronized public String run(String inputLine) throws Exception {

        HashMap<String, HashMap<String, String>> backupTerms = new HashMap<>();

        StringBuffer sb = new StringBuffer();
        sb.append(inputLine.replaceAll("[0-9]", "0"));
        sb.append("\n");

        String transformedStr = sb.toString();
        LOGGER.info("Sending: {}", transformedStr.trim());

        try {
            stdin.write(transformedStr.getBytes());
            stdin.flush();
        } catch (Exception e) {
            e.printStackTrace();

            if (numOfRestarts < maxNumOfRestarts) {
                numOfRestarts++;
                LOGGER.info(String.format("Trying to restart eu.fbk.dh.aligner.Aligner [%d/%d]", numOfRestarts, maxNumOfRestarts));
                init(true);
                stdin.write(transformedStr.getBytes());
                stdin.flush();
            }
        }

        String line;
        line = brCleanUp.readLine();
        LOGGER.info("Result: {}", line);
        return line;
    }
}
