package Encoder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import Logger.Logger;
import Executer.Executer;

public class Encoder implements Executer {
    private static final String splitDelim = " |:|="; // Delimiter in config file
    private static final String delim = " ";          // Delimiter in table of probabilities file
    private static final String endl = "\n";
    private DataInputStream inputFile;
    private String inputFileName;
    private DataOutputStream outputFile;
    private String tableFile;
    private Map<Byte, Double> probability;    // Map of letters to their probabilities
    private Map<Byte, Segment> segs;          // Map of letters to their segments
    private int textLen, numSeq, blockSize, dataLen;
    private Executer nextExecuter;  // Reference to next executor

    private enum targetType {
        ENCODE,
        DECODE
    }

    private enum tableMethodType {
        READ,
        WRITE
    }

    private enum confTypes {
        BLOCK_SIZE,
        SEQUENCE_LEN,
        TEXT_LEN,
        PROBABILITY,
        DECODE_CONF,
        TARGET,
        TABLE_FILE,
        TABLE_METHOD
    }
    private targetType target = targetType.ENCODE;
    private static final Map<String, targetType> tMap;          // Map target name to target type
    private static final Map<String, confTypes> configMap;      // Map config name to config type
    private static final Map<String, tableMethodType> metMap;   // Map table method name to table method type

    static {
        tMap = new HashMap<>();
        tMap.put("encode", targetType.ENCODE);
        tMap.put("decode", targetType.DECODE);

        configMap = new HashMap<>();
        configMap.put("num", confTypes.SEQUENCE_LEN);
        configMap.put("len", confTypes.TEXT_LEN);
        configMap.put("prob", confTypes.PROBABILITY);
        configMap.put("decconf", confTypes.DECODE_CONF);
        configMap.put("target", confTypes.TARGET);
        configMap.put("block", confTypes.BLOCK_SIZE);
        configMap.put("table", confTypes.TABLE_FILE);
        configMap.put("table_method", confTypes.TABLE_METHOD);

        metMap = new HashMap<>();
        metMap.put("read", tableMethodType.READ);
        metMap.put("write", tableMethodType.WRITE);
    }

    /**
     * Encoder constructor
     * @param inFile: input file name for setting probability table
     * @param confFile: config file
     * @throws IOException
     */
    public Encoder(String inFile, String confFile) throws IOException {
        probability = new HashMap<>();
        segs = new HashMap<>();
        inputFileName = inFile;
        textLen = 0;
        setConfigs(confFile);
    }

    /**
     * Set encoder configs from file
     * @param confFile
     * @throws IOException
     */
    private void setConfigs(String confFile) throws IOException {

        BufferedReader configReader = new BufferedReader(new FileReader(confFile));
        String line;
        while ((line = configReader.readLine()) != null) {
            String[] words = line.split(splitDelim);
            if (words.length != 2 && words.length != 3)
                throw new IOException("Wrong number of arguments in file: " + confFile + " at: " + line);
            confTypes type = configMap.get(words[0]);
            if (type == null)
                throw new IOException("Unknown config: " + words[0] + " in file: " + confFile + " at: " + line);
            switch (type) {
                case SEQUENCE_LEN: {
                    numSeq = Integer.parseInt(words[1]);
                    break;
                }
                case TEXT_LEN: {
                    textLen = Integer.parseInt(words[1]);
                    break;
                }
                case PROBABILITY: {
                    byte ch = (byte) Integer.parseInt(words[1]);
                    probability.put(ch, Double.parseDouble(words[2]));
                    segs.put(ch, new Segment());
                    break;
                }
                case TARGET: {
                    target = tMap.get(words[1]);
                    if (target == null)
                        throw new IOException("Unknown target: " + words[1] + " in file: " + confFile + " at: " + line + " decode|encode expected");
                    break;
                }
                case BLOCK_SIZE: {
                    blockSize = Integer.parseInt(words[1]);
                    break;
                }
                case TABLE_FILE: {
                    tableFile = words[1];
                    break;
                }
                case TABLE_METHOD: {
                    tableMethodType tm = metMap.get(words[1]);
                    if (tm == null)
                        throw new IOException("Unknown method: " + words[1] + "in file: " + confFile + " at: " + line + " read|write expected");
                    switch (tm) {
                        case READ: {
                            setConfigs(tableFile);
                            break;
                        }
                        case WRITE: {
                            countProb();
                            writeDecodeConf();
                        }
                    }
                    break;
                }
            }
        }
        configReader.close();
        Logger.writeLn("Configs have been set");
    }

    /**
     * Count probabilities of letters in input file
     * @throws IOException
     */
    private void countProb() throws IOException {
        DataInputStream copy = new DataInputStream(new FileInputStream(inputFileName));
        while (copy.available() > 0) {
            byte ch = copy.readByte();
            textLen++;
            if (!probability.containsKey(ch))
                probability.put(ch, 1.0);
            else
                probability.replace(ch, probability.get(ch) + 1);

            segs.putIfAbsent(ch, new Segment());
        }

        copy.close();

        for (Byte key : probability.keySet())
            probability.replace(key, probability.get(key) / textLen);
        Logger.writeLn("Probability have been counted");
    }

    /**
     * Set segments of letters
     */
    private void defineSegments() {
        double l = 0;

        for (Map.Entry<Byte, Segment> entry : segs.entrySet()) {
            entry.getValue().left = l;
            entry.getValue().right = l + probability.get(entry.getKey());
            l = entry.getValue().right;
        }
    }

    /**
     * Write table file
     * @throws IOException
     */
    private void writeDecodeConf() throws IOException {
        BufferedWriter encWriter = new BufferedWriter(new FileWriter(tableFile));

        for (Map.Entry<String, confTypes> entry : configMap.entrySet()) {
            switch (entry.getValue()) {
                case SEQUENCE_LEN: {
                    encWriter.write(entry.getKey() + delim + numSeq + endl);
                    break;
                }
                case PROBABILITY: {
                    for (Map.Entry<Byte, Double> prEntry : probability.entrySet()) {
                        encWriter.write(entry.getKey() + delim + prEntry.getKey() + delim + prEntry.getValue() + endl);
                    }
                    break;
                }
            }
        }
        encWriter.close();
    }

    /**
     * Code data
     * @param data
     * @return Object: coded data
     */
    public Object code(Object data) {
        switch (target) {
            case ENCODE: {
                try {
                    return encode((byte[]) data);
                } catch (IOException ex){
                    Logger.writeLn("Encoding Error!");
                    Logger.writeErrorLn(ex);
                    System.exit(1);
                }
                break;
            }
            case DECODE: {
                try {
                    return decode((double [])data);
                } catch (IOException ex) {
                    Logger.writeLn("Decoding Error!");
                    Logger.writeErrorLn(ex);
                    System.exit(1);
                }
                break;
            }
        }
        return null;
    }

    /**
     * Encode bytes array
     * @param data
     * @return double[]: array of encoded double
     * @throws IOException
     */
    private double[] encode(byte[] data) throws IOException {
        Logger.writeLn("Encoding...");
        defineSegments();

        int size = (int) Math.ceil((double) data.length / numSeq);
        double[] newData = new double[size];

        for (int i = 0; i < size; i++) {
            double left = 0, right = 1;
            for (int j = 0; j < numSeq; j++) {
                if (i * numSeq + j >= dataLen)
                    break;
                byte ch = data[i * numSeq + j];
                double newR = left + (right - left) * segs.get(ch).right;
                double newL = left + (right - left) * segs.get(ch).left;
                right = newR;
                left = newL;
            }
            newData[i] = (left + right) / 2;
        }
        Logger.writeLn("Encoding finished!!");
        return newData;
    }

    /**
     * Decode from array of doubles
     * @param data
     * @return byte[]: decoded byte array
     * @throws IOException
     */
    private byte[] decode(double[] data) throws IOException {
        Logger.writeLn("Decoding...");
        defineSegments();

        byte[] newData = new byte[numSeq * data.length];

        for (int i = 0; i < data.length; i++) {
            double code = data[i];
            for (int j = 0; j < numSeq; j++) {
                for (Map.Entry<Byte, Segment> entry : segs.entrySet())
                    if (code >= entry.getValue().left && code < entry.getValue().right) {
                        newData[numSeq * i + j] = entry.getKey();
                        code = (code - entry.getValue().left) / (entry.getValue().right - entry.getValue().left);
                        break;
                    }
            }
        }
        Logger.writeLn("Decoding finished!!!");
        return newData;
    }

    /**
     * Set consumer to executor
     * @param consumer
     * @throws IOException
     */
    @Override
    public void setConsumer(Executer consumer) {
        nextExecuter = consumer;
    }

    /**
     * Set output stream
     * @param output
     */
    @Override
    public void setOutput(DataOutputStream output) {
        outputFile = output;
    }

    /**
     * Set input stream
     * @param input
     */
    @Override
    public void setInput(DataInputStream input) {
        inputFile = input;
    }

    /**
     * Run function
     */
    @Override
    public void run() throws IOException {
        while (inputFile.available() > 0) {
            byte[] data = new byte[blockSize];
            if (inputFile.available() > blockSize)
                dataLen = blockSize;
            else
                dataLen = inputFile.available();
            inputFile.read(data, 0, dataLen);
            if (nextExecuter != null)
                nextExecuter.put(code(data));
            else {
                outputFile.write((byte[]) code(data));
            }
        }
    }

    /**
     * Transfer data to consumers
     * @param Data
     * @throws IOException
     */
    @Override
    public void put(Object Data) throws IOException {
        switch (target) {
            case ENCODE: {
                dataLen = ((byte[]) Data).length;
                // reading by blocks
                for (int i = 0; i < dataLen ; i++) {
                    int size = dataLen  - i > blockSize ? blockSize : dataLen  - i;
                    byte[] data = new byte[size];
                    System.arraycopy(Data, i, data, 0, size);
                    i += blockSize;
                    if (nextExecuter != null)
                        nextExecuter.put(encode(data));
                    else {
                        double[] out = encode(data);
                        for (int j = 0; j < out.length; j++)
                            outputFile.writeDouble(out[j]);
                    }
                }
                break;
            }
            case DECODE: {
                dataLen = ((double[]) Data).length;
                // reading by blocks
                for (int i = 0; i < dataLen ; i++) {
                    int size = dataLen  - i > blockSize ? blockSize : dataLen  - i;
                    double[] data = new double[size];
                    System.arraycopy(Data, i, data, 0, size);
                    i += blockSize;
                    if (nextExecuter != null)
                        nextExecuter.put(decode(data));
                    else {
                        byte[] out = decode(data);
                        for (int j = 0; j < out.length; j++)
                            outputFile.write(out[j]);
                    }
                }
                break;
            }
        }
    }
}
