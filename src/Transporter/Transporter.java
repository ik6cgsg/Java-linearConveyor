package Transporter;

import Executer.Executer;
import Logger.Logger;
import Encoder.Encoder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Conveyor of executors
 */
public class Transporter {
    private static final String splitDelim = " |:|="; // Delimiter in config file
    private ArrayList<Executer> exs;                  // Array of all executors in conveyor
    private DataInputStream inputFile;
    private String inputFileName;
    private DataOutputStream outputFile;

    /**
     * Types of params in config
     */
    private enum valTypes {
        EXECUTOR
    }
    private static final Map<String, valTypes> mapTypes; // Map of params name to types of params
    static {
        mapTypes = new HashMap<>();
        mapTypes.put("executor", valTypes.EXECUTOR);
    }

    /**
     * Conveyor constructor
     * @param inFile
     * @param outFile
     * @param confFile
     * @throws IOException
     */
    public Transporter(String inFile, String outFile, String confFile) throws IOException {
        inputFile = new DataInputStream(new FileInputStream(inFile));
        inputFileName = inFile;
        outputFile = new DataOutputStream(new FileOutputStream(outFile));
        exs = new ArrayList<>();
        setConfigs(confFile);
        if (exs.isEmpty())
            throw new IOException("Empty list of executors");
        introduce();
    }

    /**
     * Set configs of conveyor from config file
     * @param confFile
     * @throws IOException
     */
    private void setConfigs(String confFile) throws IOException {
        BufferedReader configReader = new BufferedReader(new FileReader(confFile));
        String line;
        while ((line = configReader.readLine()) != null) {
            String[] words = line.split(splitDelim);
            if (words.length != 2)
                throw new IOException("Wrong number of arguments in file: " + confFile + " at: " + line);
            valTypes type = mapTypes.get(words[0]);
            if (type == null)
                throw new IOException("Unknown config: " + words[0] + " in file: " + confFile + " at: " + line);
            switch (type) {
                case EXECUTOR: {
                    exs.add(new Encoder(inputFileName, words[1]));
                    break;
                }
            }
        }
    }

    /**
     * Acquaint executors
     * @throws IOException
     */
    private void introduce() {
        exs.get(0).setInput(inputFile);

        for (int i = 1; i < exs.size(); i++) {
           exs.get(i - 1).setConsumer(exs.get(i));
        }

        exs.get(exs.size() - 1).setOutput(outputFile);
    }

    /**
     * Conveyor start function
     */
    public void run() {
        try {
            exs.get(0).run();
            inputFile.close();
            outputFile.close();
        } catch (IOException ex) {
            Logger.writeLn("Conveyer error! ");
            Logger.writeErrorLn(ex);
        }
    }
}
