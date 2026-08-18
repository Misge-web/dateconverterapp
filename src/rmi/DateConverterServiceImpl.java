package rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Server-side implementation of DateConverterService.
 *
 * Threading model
 * ───────────────
 * RMI already dispatches each incoming call on its own thread from an
 * internal thread pool.  We additionally maintain our own fixed-size
 * ExecutorService so that heavy work (e.g. bulk step generation) can be
 * offloaded without blocking the RMI dispatch thread, and to demonstrate
 * explicit server-side threading as required by the spec.
 *
 * Multiple clients
 * ────────────────
 * Because UnicastRemoteObject is thread-safe by default (each call gets
 * its own stack frame), multiple clients can call the service concurrently
 * without any extra synchronisation.  An AtomicInteger tracks the live
 * request count for logging purposes.
 */
public class DateConverterServiceImpl extends UnicastRemoteObject
        implements DateConverterService {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = ConversionLogger.get();

    // Ethiopian calendar epoch: JDN of 1 Meskerem 1 AM
    private static final long ETHIOPIAN_EPOCH = 1724221L;

    // Thread pool for server-side async work
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r, "converter-worker");
                        t.setDaemon(true);
                        return t;
                    });

    // Live request counter (for logging)
    private static final AtomicInteger ACTIVE = new AtomicInteger(0);

    // Ethiopian month names (1-based)
    private static final String[] ETH_MONTHS = {
        "", "Meskerem","Tikimt","Hidar","Tahsas",
        "Tir","Yekatit","Megabit","Miazia",
        "Ginbot","Sene","Hamle","Nehase","Pagume"
    };

    // Gregorian month names (1-based)
    private static final String[] GREG_MONTHS = {
        "", "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    public DateConverterServiceImpl() throws RemoteException {
        super();
    }

    // =========================================================================
    // Remote interface
    // =========================================================================

    @Override
    public String ethiopianToGregorian(int day, int month, int year)
            throws RemoteException, DateConverterException {

        int req = ACTIVE.incrementAndGet();
        LOG.info(String.format("[req#%d] ETH→GREG  %02d/%02d/%04d  (active=%d)",
                req, day, month, year, req));
        try {
            validateEthiopian(day, month, year);
            long jdn = ethiopianToJDN(day, month, year);
            int[] g  = jdnToGregorian(jdn);
            String result = formatDate(g[0], g[1], g[2]);
            LOG.info(String.format("[req#%d] result → %s", req, result));
            return result;
        } finally {
            ACTIVE.decrementAndGet();
        }
    }

    @Override
    public String gregorianToEthiopian(int day, int month, int year)
            throws RemoteException, DateConverterException {

        int req = ACTIVE.incrementAndGet();
        LOG.info(String.format("[req#%d] GREG→ETH  %02d/%02d/%04d  (active=%d)",
                req, day, month, year, req));
        try {
            validateGregorian(day, month, year);
            long jdn = gregorianToJDN(day, month, year);
            int[] e  = jdnToEthiopian(jdn);
            String result = formatDate(e[0], e[1], e[2]);
            LOG.info(String.format("[req#%d] result → %s", req, result));
            return result;
        } finally {
            ACTIVE.decrementAndGet();
        }
    }

    @Override
    public String[] getConversionSteps(int day, int month, int year, String direction)
            throws RemoteException, DateConverterException {

        LOG.info(String.format("[steps] %s  %02d/%02d/%04d", direction, day, month, year));

        // Offload step generation to the worker pool and wait for the result.
        // This demonstrates server-side threading: the RMI dispatch thread is
        // freed immediately; the worker thread does the computation.
        try {
            return POOL.submit(() -> buildSteps(day, month, year, direction)).get();
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DateConverterException)
                throw (DateConverterException) cause;
            throw new RemoteException("Step generation failed", cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Step generation interrupted");
        }
    }

    // =========================================================================
    // Step generation
    // =========================================================================

    private String[] buildSteps(int day, int month, int year, String direction)
            throws DateConverterException {

        List<String> steps = new ArrayList<>();

        if ("ETH_TO_GREG".equals(direction)) {
            validateEthiopian(day, month, year);

            long jdn    = ethiopianToJDN(day, month, year);
            int[] g     = jdnToGregorian(jdn);
            long leaps  = (year - 1) / 4;
            long y365   = 365L * (year - 1);
            long m30    = 30L  * (month - 1);
            long dOff   = day - 1;

            steps.add("╔══════════════════════════════════════════════════════╗");
            steps.add("  Ethiopian  →  Gregorian  Conversion");
            steps.add("╚══════════════════════════════════════════════════════╝");
            steps.add("");
            steps.add(String.format(
                "  Input   :  Day %d  of  %s  (month %d),  Year %d  [Ethiopian]",
                day, ETH_MONTHS[month], month, year));
            steps.add("");
            steps.add("─── Step 1 : Establish the Ethiopian Epoch ───────────────");
            steps.add(String.format(
                "  The Ethiopian calendar starts on 1 Meskerem 1 AM."));
            steps.add(String.format(
                "  That date equals Julian Day Number (JDN) = %,d", ETHIOPIAN_EPOCH));
            steps.add("");
            steps.add("─── Step 2 : Count Completed Leap Cycles ─────────────────");
            steps.add(String.format(
                "  Ethiopian leap years occur every 4 years (year %% 4 == 3)."));
            steps.add(String.format(
                "  Completed cycles before year %d  =  (%d - 1) / 4  =  %d",
                year, year, leaps));
            steps.add("");
            steps.add("─── Step 3 : Build the JDN Formula ───────────────────────");
            steps.add("  JDN  =  Epoch");
            steps.add("       +  365 × (year - 1)       ← days in full years");
            steps.add("       +  floor((year-1) / 4)    ← extra leap days");
            steps.add("       +  30  × (month - 1)      ← days in full months");
            steps.add("       +  (day - 1)              ← days into this month");
            steps.add("");
            steps.add("─── Step 4 : Substitute Values ───────────────────────────");
            steps.add(String.format(
                "  JDN  =  %,d", ETHIOPIAN_EPOCH));
            steps.add(String.format(
                "       +  365 × (%d - 1)  =  %,d", year, y365));
            steps.add(String.format(
                "       +  %,d              (leap days)", leaps));
            steps.add(String.format(
                "       +  30  × (%d - 1)  =  %,d", month, m30));
            steps.add(String.format(
                "       +  (%d - 1)         =  %,d", day, dOff));
            steps.add(String.format(
                "       ─────────────────────────────"));
            steps.add(String.format(
                "  JDN  =  %,d + %,d + %,d + %,d + %,d  =  %,d",
                ETHIOPIAN_EPOCH, y365, leaps, m30, dOff, jdn));
            steps.add("");
            steps.add("─── Step 5 : JDN → Gregorian (Astronomical Algorithm) ────");
            steps.add(String.format(
                "  JDN %,d  maps to  %02d-%02d-%04d  in the Gregorian calendar.",
                jdn, g[0], g[1], g[2]));
            steps.add("");
            steps.add("─── Result ────────────────────────────────────────────────");
            steps.add(String.format(
                "  %d %s %d  (Ethiopian)   →   %d %s %d  (Gregorian)",
                day, ETH_MONTHS[month], year,
                g[0], GREG_MONTHS[g[1]], g[2]));
            steps.add(String.format(
                "  Formatted  :  %02d-%02d-%04d", g[0], g[1], g[2]));

        } else if ("GREG_TO_ETH".equals(direction)) {
            validateGregorian(day, month, year);

            long jdn  = gregorianToJDN(day, month, year);
            int[] e   = jdnToEthiopian(jdn);

            int  a    = (14 - month) / 12;
            int  yy   = year + 4800 - a;
            int  mm   = month + 12 * a - 3;
            long diff = jdn - ETHIOPIAN_EPOCH;
            long quad = diff / 1461;
            long rem  = diff % 1461;
            int  yic  = (int) Math.min(rem / 365, 3);
            long remAfterYear = rem - (long) yic * 365;

            steps.add("╔══════════════════════════════════════════════════════╗");
            steps.add("  Gregorian  →  Ethiopian  Conversion");
            steps.add("╚══════════════════════════════════════════════════════╝");
            steps.add("");
            steps.add(String.format(
                "  Input   :  %d %s %d  [Gregorian]",
                day, GREG_MONTHS[month], year));
            steps.add("");
            steps.add("─── Step 1 : Gregorian → JDN (Astronomical Algorithm) ────");
            steps.add("  Uses the standard proleptic Gregorian formula:");
            steps.add("  a  =  (14 - month) / 12");
            steps.add("  y  =  year + 4800 - a");
            steps.add("  m  =  month + 12×a - 3");
            steps.add("  JDN = day + (153×m+2)/5 + 365×y + y/4 - y/100 + y/400 - 32045");
            steps.add("");
            steps.add("─── Step 2 : Substitute Values ───────────────────────────");
            steps.add(String.format(
                "  a   =  (14 - %d) / 12  =  %d", month, a));
            steps.add(String.format(
                "  y   =  %d + 4800 - %d  =  %d", year, a, yy));
            steps.add(String.format(
                "  m   =  %d + 12×%d - 3  =  %d", month, a, mm));
            steps.add(String.format(
                "  JDN =  %,d", jdn));
            steps.add("");
            steps.add("─── Step 3 : Establish the Ethiopian Epoch ───────────────");
            steps.add(String.format(
                "  Ethiopian epoch JDN  =  %,d  (1 Meskerem 1 AM)", ETHIOPIAN_EPOCH));
            steps.add(String.format(
                "  Days since epoch     =  %,d - %,d  =  %,d days",
                jdn, ETHIOPIAN_EPOCH, diff));
            steps.add("");
            steps.add("─── Step 4 : Decompose into 4-Year Cycles ────────────────");
            steps.add("  Each 4-year cycle = 365×4 + 1 = 1461 days");
            steps.add(String.format(
                "  Completed cycles  =  %,d / 1461  =  %d", diff, quad));
            steps.add(String.format(
                "  Remaining days    =  %,d %% 1461  =  %d", diff, rem));
            steps.add("");
            steps.add("─── Step 5 : Find Year Within Cycle ──────────────────────");
            steps.add(String.format(
                "  Year index in cycle  =  min(%d / 365, 3)  =  %d", rem, yic));
            steps.add(String.format(
                "  Ethiopian year       =  4 × %d + %d + 1  =  %d", quad, yic, e[2]));
            steps.add(String.format(
                "  Days remaining after year offset  =  %d - %d×365  =  %d",
                rem, yic, remAfterYear));
            steps.add("");
            steps.add("─── Step 6 : Find Month and Day ──────────────────────────");
            steps.add(String.format(
                "  Month  =  %d / 30 + 1  =  %d  (%s)",
                remAfterYear, e[1], ETH_MONTHS[e[1]]));
            steps.add(String.format(
                "  Day    =  %d %% 30 + 1  =  %d",
                remAfterYear, e[0]));
            steps.add("");
            steps.add("─── Result ────────────────────────────────────────────────");
            steps.add(String.format(
                "  %d %s %d  (Gregorian)   →   %d %s %d  (Ethiopian)",
                day, GREG_MONTHS[month], year,
                e[0], ETH_MONTHS[e[1]], e[2]));
            steps.add(String.format(
                "  Formatted  :  %02d-%02d-%04d", e[0], e[1], e[2]));

        } else {
            throw new DateConverterException("Unknown direction: " + direction);
        }

        return steps.toArray(new String[0]);
    }

    // =========================================================================
    // JDN conversion core
    // =========================================================================

    private long ethiopianToJDN(int day, int month, int year) {
        return ETHIOPIAN_EPOCH
                + 365L * (year - 1)
                + (long) ((year - 1) / 4)
                + 30L  * (month - 1)
                + (day - 1);
    }

    private int[] jdnToEthiopian(long jdn) {
        long diff = jdn - ETHIOPIAN_EPOCH;
        long quad = diff / 1461;
        long rem  = diff % 1461;
        int  yic  = (int) Math.min(rem / 365, 3);
        rem -= (long) yic * 365;
        int year  = (int) (quad * 4 + yic + 1);
        int month = (int) (rem / 30 + 1);
        int day   = (int) (rem % 30 + 1);
        return new int[]{day, month, year};
    }

    private long gregorianToJDN(int day, int month, int year) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153L * m + 2) / 5 + 365L * y
                + y / 4 - y / 100 + y / 400 - 32045L;
    }

    private int[] jdnToGregorian(long jdn) {
        long a = jdn + 32044L;
        long b = (4 * a + 3) / 146097L;
        long c = a - (146097L * b) / 4;
        long d = (4 * c + 3) / 1461L;
        long e = c - (1461L * d) / 4;
        long m = (5 * e + 2) / 153L;
        int day   = (int) (e - (153L * m + 2) / 5 + 1);
        int month = (int) (m + 3 - 12 * (m / 10));
        int year  = (int) (100 * b + d - 4800 + m / 10);
        return new int[]{day, month, year};
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private void validateEthiopian(int day, int month, int year)
            throws DateConverterException {
        if (year < 1)
            throw new DateConverterException("Ethiopian year must be ≥ 1, got: " + year);
        if (month < 1 || month > 13)
            throw new DateConverterException("Ethiopian month must be 1–13, got: " + month);
        int maxDay = (month == 13) ? (isEthiopianLeapYear(year) ? 6 : 5) : 30;
        if (day < 1 || day > maxDay)
            throw new DateConverterException(
                "Day must be 1–" + maxDay + " for " + ETH_MONTHS[month] + ", got: " + day);
    }

    private void validateGregorian(int day, int month, int year)
            throws DateConverterException {
        if (year < 1)
            throw new DateConverterException("Gregorian year must be ≥ 1, got: " + year);
        if (month < 1 || month > 12)
            throw new DateConverterException("Gregorian month must be 1–12, got: " + month);
        int maxDay = daysInGregorianMonth(month, year);
        if (day < 1 || day > maxDay)
            throw new DateConverterException(
                "Day must be 1–" + maxDay + " for month " + month + "/" + year + ", got: " + day);
    }

    private boolean isEthiopianLeapYear(int year) { return (year % 4) == 3; }

    private boolean isGregorianLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    private int daysInGregorianMonth(int month, int year) {
        int[] days = {0,31,28,31,30,31,30,31,31,30,31,30,31};
        if (month == 2 && isGregorianLeapYear(year)) return 29;
        return days[month];
    }

    private String formatDate(int day, int month, int year) {
        return String.format("%02d-%02d-%04d", day, month, year);
    }
}
