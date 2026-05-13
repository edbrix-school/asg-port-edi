package com.asg.portediintegration.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * EDI validation utilities
 * Provides quick validation and delegates to comprehensive validator
 */
@Slf4j
public class EdiValidationUtils {

    /**
     * Quick validation of EDI file type from InputStream.
     * This is a lightweight check that only validates basic file signature.
     * For comprehensive validation including schema, checksum, and structure validation,
     * use EdiFileValidator.validateEdiFile() instead.
     *
     * @param inputStream The source input stream
     * @param fileName    File name for extension check
     * @return true if valid, false otherwise
     * @throws IOException
     */
    public static boolean validateEdiFile(InputStream inputStream, String fileName) throws IOException {
        if (inputStream == null) {
            log.warn("Skipping EDI validation: null input stream");
            return false;
        }
        if (StringUtils.isBlank(fileName)) {
            log.warn("Skipping EDI file with null or empty filename");
            return false;
        }

        String lowerName = fileName.toLowerCase();

        // Check extension first
        if (!lowerName.endsWith(".edi") && !lowerName.endsWith(".x12") && !lowerName.endsWith(".xml") && !lowerName.endsWith(".txt")) {
            log.warn("Invalid EDI file extension: {}", fileName);
            return false;
        }

        // Read first 4-8 bytes for magic number / signature
        inputStream.mark(16); // mark the stream so we can reset after reading
        byte[] header = new byte[8];
        int bytesRead = inputStream.read(header);
        inputStream.reset(); // reset for downstream processing

        if (bytesRead <= 0) {
            log.warn("Empty or unreadable file: {}", fileName);
            return false;
        }

        String headerStr = new String(header, 0, bytesRead).trim();
        if (headerStr.startsWith("ISA") || headerStr.startsWith("UNB") || headerStr.startsWith("<?xml")) {
            return true;
        } else {
            log.warn("EDI file signature mismatch: {} - header: {}", fileName, headerStr);
            return false;
        }
    }

    /**
     * Comprehensive validation of EDI file including structure, schema, and checksum
     *
     * @param inputStream The EDI file input stream
     * @param fileName    File name for logging
     * @return ValidationResult with detailed validation information
     */
    public static EdiFileValidator.ValidationResult validateEdiFileComprehensive(InputStream inputStream, String fileName) {
        return EdiFileValidator.validateEdiFile(inputStream, fileName);
    }
}
