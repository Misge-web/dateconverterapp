package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI remote interface for Ethiopian <-> Gregorian date conversion.
 */
public interface DateConverterService extends Remote {

    /**
     * Converts an Ethiopian date to a Gregorian date string "DD/MM/YYYY".
     */
    String ethiopianToGregorian(int day, int month, int year)
            throws RemoteException, DateConverterException;

    /**
     * Converts a Gregorian date to an Ethiopian date string "DD/MM/YYYY".
     */
    String gregorianToEthiopian(int day, int month, int year)
            throws RemoteException, DateConverterException;

    /**
     * Returns a human-readable, step-by-step explanation of how the
     * conversion was computed.  Each element in the returned array is
     * one numbered step shown in the UI's "Conversion Steps" panel.
     *
     * @param direction "ETH_TO_GREG" or "GREG_TO_ETH"
     */
    String[] getConversionSteps(int day, int month, int year, String direction)
            throws RemoteException, DateConverterException;
}
