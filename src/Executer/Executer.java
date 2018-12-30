package Executer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Executor interface
 */
public interface Executer {
    /**
     * Set consumer to executor
     * @param consumer
     * @throws IOException
     */
    void setConsumer(Executer consumer);

    /**
     * Set output stream
     * @param output
     */
    void setOutput(DataOutputStream output);

    /**
     * Set input stream
     * @param input
     */
    void setInput(DataInputStream input);

    /**
     * Run function
     */
    void run() throws IOException;

    /**
     * Transfer data to consumers
     * @param Data
     * @throws IOException
     */
    void put(Object Data) throws IOException;
}
