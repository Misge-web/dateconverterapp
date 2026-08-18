package client;

import rmi.DateConverterException;
import rmi.DateConverterService;
import rmi.DateConverterServer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * RMI client wrapper — hides all registry/stub plumbing from the UI.
 * Multiple instances can run simultaneously (one per UI window).
 */
public class DateConverterClient {

    private final String host;
    private final int    port;
    private DateConverterService service;

    public DateConverterClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public DateConverterClient() {
        this("localhost", DateConverterServer.DEFAULT_PORT);
    }

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Looks up the remote stub in the RMI registry.
     * The server is embedded in the same JVM so connection always succeeds
     * after the 800 ms startup delay in main().
     */
    public void connect() throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        service = (DateConverterService) registry.lookup(DateConverterServer.SERVICE_NAME);
        System.out.println("[RMI] Connected to " + DateConverterServer.SERVICE_NAME
                + " at " + host + ":" + port);
    }

    public void reconnect() throws Exception {
        service = null;
        connect();
    }

    public boolean isConnected() { return service != null; }

    // ── Conversion API ────────────────────────────────────────────────────────

    public String ethiopianToGregorian(int day, int month, int year)
            throws DateConverterException, Exception {
        ensureConnected();
        return service.ethiopianToGregorian(day, month, year);
    }

    public String gregorianToEthiopian(int day, int month, int year)
            throws DateConverterException, Exception {
        ensureConnected();
        return service.gregorianToEthiopian(day, month, year);
    }

    /**
     * Fetches step-by-step explanation from the server.
     *
     * @param direction "ETH_TO_GREG" or "GREG_TO_ETH"
     */
    public String[] getConversionSteps(int day, int month, int year, String direction)
            throws DateConverterException, Exception {
        ensureConnected();
        return service.getConversionSteps(day, month, year, direction);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void ensureConnected() throws Exception {
        if (!isConnected()) connect();
    }
}
