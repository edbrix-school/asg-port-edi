package com.asg.portediintegration.service;

import java.io.InputStream;

/**
 * Service for scanning files for viruses and malware
 */
public interface VirusScanService {

    /**
     * Scan result for virus/malware scanning
     */
    record ScanResult(boolean clean, String threatName, String details, String scanEngine, long scanDurationMs) {

        public static ScanResult clean(String scanEngine, long scanDurationMs) {
            return new ScanResult(true, null, "No threats detected", scanEngine, scanDurationMs);
        }

        public static ScanResult infected(String threatName, String details, String scanEngine, long scanDurationMs) {
            return new ScanResult(false, threatName, details, scanEngine, scanDurationMs);
        }
    }

    /**
     * Scan a file for viruses and malware
     *
     * @param inputStream File input stream to scan
     * @param fileName    File name for logging
     * @return ScanResult indicating if file is clean or infected
     */
    ScanResult scanFile(InputStream inputStream, String fileName);

    /**
     * Check if virus scanning is enabled
     *
     * @return true if virus scanning is enabled, false otherwise
     */
    boolean isScanningEnabled();
}
