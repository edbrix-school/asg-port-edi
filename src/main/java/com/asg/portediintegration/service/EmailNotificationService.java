package com.asg.portediintegration.service;

import java.util.List;

/**
 * Service for sending email notifications for EDI processing failures
 */
public interface EmailNotificationService {

    /**
     * Send email notification for validation failure
     *
     * @param fileName Name of the file that failed validation
     * @param errors   List of validation errors
     * @param warnings List of validation warnings
     * @param checksum File checksum if available
     */
    void sendValidationFailureNotification(String fileName, List<String> errors,
                                           List<String> warnings, String checksum);

    /**
     * Send email notification for file corruption
     *
     * @param fileName     Name of the corrupted file
     * @param errorMessage Error message describing the corruption
     * @param checksum     File checksum if available
     */
    void sendFileCorruptionNotification(String fileName, String errorMessage, String checksum);

    /**
     * Send email notification for parsing failure
     *
     * @param fileName     Name of the file that failed parsing
     * @param errorMessage Error message describing the parsing failure
     * @param lineNumber   Line number where parsing failed (if available)
     */
    void sendParsingFailureNotification(String fileName, String errorMessage, Integer lineNumber);

    /**
     * Send email notification for extraction failure
     *
     * @param fileName     Name of the file where extraction failed
     * @param fieldName    Field name that failed to extract
     * @param errorMessage Error message describing the extraction failure
     */
    void sendExtractionFailureNotification(String fileName, String fieldName, String errorMessage);

    /**
     * Send email notification for virus/malware detection
     *
     * @param fileName   Name of the infected file
     * @param threatName Name of the detected threat
     * @param scanResult Detailed scan result
     */
    void sendVirusDetectionNotification(String fileName, String threatName, String scanResult);

    /**
     * Send email notification for processing failure
     *
     * @param fileName     Name of the file that failed processing
     * @param errorMessage Error message describing the processing failure
     * @param exception    Exception that occurred (if any)
     */
    void sendProcessingFailureNotification(String fileName, String errorMessage, Exception exception);

    /**
     * Send email notification for checksum mismatch
     *
     * @param fileName         Name of the file with checksum mismatch
     * @param expectedChecksum Expected checksum value
     * @param actualChecksum   Actual checksum value
     */
    void sendChecksumMismatchNotification(String fileName, String expectedChecksum, String actualChecksum);

    /**
     * Send email notification for duplicate file detection
     *
     * @param fileName        Name of the duplicate file
     * @param checksum        File checksum
     * @param duplicateReason Reason for duplicate detection (e.g., "Same checksum", "Same UNB line")
     */
    void sendDuplicateFileNotification(String fileName, String checksum, String duplicateReason);
}
