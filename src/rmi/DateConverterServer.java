package rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Logger;

/**
 * Starts the RMI registry and binds the DateConverterService.
 *
 * Multiple clients are supported out of the box: RMI dispatches each
 * incoming call on its own thread, and DateConverterServiceImpl uses an
 * internal ExecutorService for heavy work.  No extra configuration needed.
 *
 * Usage:  java -cp out rmi.DateConverterServer [port]
 */
public class DateConverterServer {

    public static final int    DEFAULT_PORT  = 1099;
    public static final String SERVICE_NAME  = "DateConverterService";

    public static void main(String[] args) {
        Logger log = ConversionLogger.get();

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warning("Invalid port argument, using default " + DEFAULT_PORT);
            }
        }

        try {
            DateConverterServiceImpl service = new DateConverterServiceImpl();
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(SERVICE_NAME, service);

            log.info("================================================");
            log.info("  Date Converter RMI Server started");
            log.info("  Port        : " + port);
            log.info("  Service     : " + SERVICE_NAME);
            log.info("  Threads     : " + Runtime.getRuntime().availableProcessors() + " worker(s)");
            log.info("  Log file    : logs/server.log");
            log.info("  Multi-client: YES  (each call dispatched on its own thread)");
            log.info("================================================");
            log.info("Waiting for clients…  Press Ctrl+C to stop.");

        } catch (Exception e) {
            log.severe("Server failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
