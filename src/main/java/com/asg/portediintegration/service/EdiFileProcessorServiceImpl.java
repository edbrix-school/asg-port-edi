package com.asg.portediintegration.service;

import com.asg.portediintegration.repository.EdiUploadCodecoRepository;
import com.asg.portediintegration.utils.EdiFileValidator;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdiFileProcessorServiceImpl implements EdiFileProcessorService {

    private final OutlookEmailService outlookEmailService;
    private final EdiFileService ediFileService;
    private final EmailNotificationService emailNotificationService;
    private final VirusScanService virusScanService;
    private final GlobalParameterService globalParameterService;
    private final EdiUploadCodecoRepository ediUploadCodecoRepository;

    private static final String MAX_FILE_SIZE_KEY = "PORT_EDI_MAX_FILE_SIZE";

    /**
     * Processes EDI files from unread messages
     */
    @Override
    public List<Path> processEdiFilesFromInbox() {
        log.info("Starting EDI file processing from Outlook inbox");
        return processEdiFiles(outlookEmailService::fetchUnreadMessages);
    }

    /**
     * Processes EDI files from all messages
     */
    @Override
    public List<Path> processEdiFilesFromAllMessages() {
        log.info("Starting EDI file processing from all Outlook inbox messages");
        return processEdiFiles(outlookEmailService::fetchAllMessages);
    }

    /**
     * Generic method to process EDI files from messages fetched by the given supplier
     */
    private List<Path> processEdiFiles(Supplier<List<Message>> messageFetcher) {
        List<Path> processedFiles = new ArrayList<>();

        try {
            List<Message> messages = messageFetcher.get();
            if (messages == null) {
                messages = List.of();
            }

            log.info("Starting EDI file processing from fetched {} file(s)", messages.size());

            for (Message message : messages) {
                try {
                    log.info("Processing message: {} - Subject: {}", message.id, message.subject);

                    List<FileAttachment> attachments = outlookEmailService.getAttachments(message.id);
                    List<FileAttachment> ediAttachments = outlookEmailService.filterEdiAttachments(attachments);

                    if (ediAttachments.isEmpty()) {
                        log.info("No EDI attachments found in message: {}", message.id);
                        continue;
                    }

                    log.info("Found {} EDI attachment(s) in message: {}", ediAttachments.size(), message.id);

                    for (FileAttachment ediAttachment : ediAttachments) {
                        processEdiAttachment(message, ediAttachment)
                                .ifPresent(processedFiles::add);
                    }

                    outlookEmailService.markMessageAsRead(message.id);
                    log.info("Marked message as read: {}", message.id);

                } catch (Exception e) {
                    log.error("Error processing message: {}", message.id, e);
                }
            }

            log.info("Completed EDI file processing. Processed {} file(s)", processedFiles.size());
            return processedFiles;

        } catch (Exception e) {
            log.error("Error during EDI file processing", e);
            throw new RuntimeException("Failed to process EDI files from inbox", e);
        }
    }

    /**
     * Process a single EDI attachment from an email message
     *
     * @param message       The email message containing the attachment
     * @param ediAttachment The EDI file attachment to process
     * @return Optional containing the saved file path if successful, empty otherwise
     */
    private Optional<Path> processEdiAttachment(Message message, FileAttachment ediAttachment) {
        if (message == null || ediAttachment == null || StringUtils.isAnyBlank(message.id, ediAttachment.id)) {
            log.warn("Cannot process EDI attachment: missing message, attachment, or ids");
            return Optional.empty();
        }
        try (InputStream attachmentStream = outlookEmailService.getAttachmentStream(message.id, ediAttachment.id)) {
            log.info("Processing EDI attachment: {}", StringUtils.defaultString(ediAttachment.name));

            byte[] fileContent = readAttachmentContent(ediAttachment, attachmentStream);
            if (fileContent == null) {
                return Optional.empty();
            }

            if (!scanForViruses(ediAttachment, fileContent)) {
                return Optional.empty();
            }

//            EdiFileValidator.ValidationResult validationResult = validateEdiFile(ediAttachment, fileContent);
//            if (validationResult == null) {
//                return Optional.empty();
//            }
//
//            detectDuplicates(ediAttachment, fileContent, validationResult);

            Path savedPath = saveEdiFile(ediAttachment, fileContent);
            if (savedPath == null) {
                return Optional.empty();
            }

            log.info("Successfully processed EDI file: {} -> {}", ediAttachment.name, savedPath);
            return Optional.of(savedPath);

        } catch (Exception e) {
            log.error("Error processing EDI attachment: {} from message: {}", StringUtils.defaultString(ediAttachment.name), message.id, e);
            emailNotificationService.sendProcessingFailureNotification(StringUtils.defaultString(ediAttachment.name), "Processing error: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Read attachment content from input stream
     *
     * @param attachment  The file attachment
     * @param inputStream The input stream to read from
     * @return File content as byte array, or null if reading fails
     */
    private byte[] readAttachmentContent(FileAttachment attachment, InputStream inputStream) {
        if (attachment == null || inputStream == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            // EMAIL NOTIFICATION: Email attachments not readable
            log.error("Failed to read EDI attachment: {} - Error: {}", StringUtils.defaultString(attachment.name), e.getMessage(), e);
            emailNotificationService.sendFileCorruptionNotification(StringUtils.defaultString(attachment.name), "Email attachment not readable: " + e.getMessage(), null);
            return null;
        }
    }

    /**
     * Scan file for viruses and malware
     *
     * @param attachment The file attachment
     * @param content    File content to scan
     * @return true if file is clean or scanning is disabled, false if threat detected
     */
    private boolean scanForViruses(FileAttachment attachment, byte[] content) {
        if (!virusScanService.isScanningEnabled()) {
            return true;
        }

        VirusScanService.ScanResult scanResult = virusScanService.scanFile(new ByteArrayInputStream(content), StringUtils.defaultString(attachment.name));

        if (!scanResult.clean()) {
            // EMAIL NOTIFICATION: Virus/malware detected
            log.error("Virus/malware detected in file: {} - Threat: {}", StringUtils.defaultString(attachment.name), scanResult.threatName());
            emailNotificationService.sendVirusDetectionNotification(StringUtils.defaultString(attachment.name), scanResult.threatName(), scanResult.details());
            return false;
        }

        log.info("Virus scan passed for file: {} (Duration: {}ms)", StringUtils.defaultString(attachment.name), scanResult.scanDurationMs());
        return true;
    }

    /**
     * Validate EDI file structure, format, and schema
     *
     * @param attachment The file attachment
     * @param content    File content to validate
     * @return ValidationResult if valid, null if validation fails
     */
    private EdiFileValidator.ValidationResult validateEdiFile(FileAttachment attachment, byte[] content) {
        try {
            Long maxFileSizeBytes = getMaxFileSizeBytes();
            EdiFileValidator.ValidationResult result = EdiFileValidator.validateEdiFile(new ByteArrayInputStream(content), StringUtils.defaultString(attachment.name), maxFileSizeBytes);

            if (!result.isValid()) {
                // EMAIL NOTIFICATION: Invalid EDI structure or format / Large file size exceeded
                // Check if it's a file size error specifically
                boolean isFileSizeError = result.getErrors().stream().anyMatch(error -> error.contains("exceeds maximum allowed size") || error.contains("File size"));

                if (isFileSizeError) {
                    // EMAIL NOTIFICATION: Large file size exceeded limit
                    log.error("File size exceeded limit: {} - Errors: {}", StringUtils.defaultString(attachment.name), result.getErrors());
                } else {
                    // EMAIL NOTIFICATION: Invalid EDI structure or format
                    log.error("Invalid EDI structure/format: {} - Errors: {}", StringUtils.defaultString(attachment.name), result.getErrors());
                }

                emailNotificationService.sendValidationFailureNotification(StringUtils.defaultString(attachment.name), result.getErrors(), result.getWarnings(), result.getChecksum());

                if (!result.getWarnings().isEmpty()) {
                    log.warn("Validation warnings for {}: {}", StringUtils.defaultString(attachment.name), result.getWarnings());
                }
                return null;
            }

            // Log validation warnings if any
            if (!result.getWarnings().isEmpty()) {
                log.warn("Validation warnings for {}: {}", StringUtils.defaultString(attachment.name), result.getWarnings());
            }

            log.info("EDI file validated successfully - Type: {}, Messages: {}, Checksum: {}", result.getMessageType(), result.getMessageCount(), result.getChecksum());

            return result;

        } catch (Exception e) {
            // EMAIL NOTIFICATION: Validation failure (exception during validation)
            log.error("Exception during validation of file: {} - Error: {}", StringUtils.defaultString(attachment.name), e.getMessage(), e);
            emailNotificationService.sendValidationFailureNotification(StringUtils.defaultString(attachment.name), List.of("Exception during validation: " + e.getMessage()), List.of(), null);
            return null;
        }
    }

    /**
     * Detect duplicate files using UNB line and checksum
     *
     * @param attachment       The file attachment
     * @param content          File content
     * @param validationResult Validation result containing checksum
     */
    private void detectDuplicates(FileAttachment attachment, byte[] content, EdiFileValidator.ValidationResult validationResult) {
        String fileChecksum = validationResult.getChecksum();
        if (StringUtils.isBlank(fileChecksum)) {
            return;
        }

        String unbLine = extractUnbLineFromContent(content);
        if (StringUtils.isBlank(unbLine)) {
            return;
        }

        String duplicateReason = null;

        // Check if UNB line already exists in database (CODECO)
        var existingCodeco = ediUploadCodecoRepository.findByTabTextAndFileLoadName(unbLine, "CODECO");
        if (!existingCodeco.isEmpty()) {
            duplicateReason = "Duplicate UNB line found (CODECO)";
        } else {
            // Check for COARRI
            var existingCoarri = ediUploadCodecoRepository.findByTabTextAndFileLoadNameForCoarri(unbLine, "COARRI");
            if (!existingCoarri.isEmpty()) {
                duplicateReason = "Duplicate UNB line found (COARRI)";
            }
        }

        // TODO: If checksum is stored in database, also check for checksum duplicates
        // if (duplicateReason == null && checksumExistsInDatabase(fileChecksum)) {
        //     duplicateReason = "Duplicate checksum found";
        // }

        if (StringUtils.isNotBlank(duplicateReason)) {
            // EMAIL NOTIFICATION: Failed checksum / duplicate detection
            log.warn("Duplicate file detected: {} - Reason: {}, Checksum: {}", StringUtils.defaultString(attachment.name), duplicateReason, fileChecksum);
            emailNotificationService.sendDuplicateFileNotification(StringUtils.defaultString(attachment.name), fileChecksum, duplicateReason);
            // Note: Duplicates are allowed, so we continue processing
        }
    }

    /**
     * Save EDI file to target folder
     *
     * @param attachment The file attachment
     * @param content    File content to save
     * @return Path to saved file, or null if save fails
     */
    private Path saveEdiFile(FileAttachment attachment, byte[] content) {
        try {
            Path savedPath = ediFileService.saveEdiFile(StringUtils.defaultString(attachment.name), new ByteArrayInputStream(content));
            return savedPath;
        } catch (Exception e) {
            // EMAIL NOTIFICATION: File save errors
            log.error("File save error for EDI file: {} - Error: {}", StringUtils.defaultString(attachment.name), e.getMessage(), e);
            emailNotificationService.sendProcessingFailureNotification(StringUtils.defaultString(attachment.name), "File save error: " + e.getMessage(), e instanceof Exception ? (Exception) e : new RuntimeException(e));
            return null;
        }
    }

    /**
     * Get maximum file size from global parameters
     * Returns size in bytes, or null to use default
     */
    private Long getMaxFileSizeBytes() {
        String maxFileSizeStr = globalParameterService.getValue(MAX_FILE_SIZE_KEY);
        if (StringUtils.isBlank(maxFileSizeStr)) {
            log.debug("Max file size not configured in global parameters, using default");
            return null; // Will use default in validator
        }

        try {
            // Support formats like "5MB", "5242880", "5" (assumes MB)
            String trimmed = maxFileSizeStr.trim().toUpperCase();
            long sizeBytes;

            if (trimmed.endsWith("MB")) {
                long mb = Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim());
                sizeBytes = mb * 1024 * 1024;
            } else if (trimmed.endsWith("KB")) {
                long kb = Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim());
                sizeBytes = kb * 1024;
            } else if (trimmed.endsWith("GB")) {
                long gb = Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim());
                sizeBytes = gb * 1024 * 1024 * 1024;
            } else {
                // Assume bytes if no unit specified, or if it's just a number assume MB
                try {
                    sizeBytes = Long.parseLong(trimmed);
                } catch (NumberFormatException e) {
                    // If parsing fails, assume it's MB
                    long mb = Long.parseLong(trimmed.replaceAll("[^0-9]", ""));
                    sizeBytes = mb * 1024 * 1024;
                }
            }

            log.debug("Max file size from global parameters: {} bytes ({} MB)", sizeBytes, sizeBytes / (1024 * 1024));
            return sizeBytes;
        } catch (NumberFormatException e) {
            log.warn("Invalid max file size format in global parameter {}: {}. Using default.", MAX_FILE_SIZE_KEY, maxFileSizeStr, e);
            return null; // Will use default in validator
        }
    }

    /**
     * Extract UNB line from file content for duplicate detection
     */
    private String extractUnbLineFromContent(byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            return null;
        }
        try {
            String content = new String(fileContent, StandardCharsets.ISO_8859_1);
            String[] lines = content.split("\r?\n");
            for (String line : lines) {
                String trimmed = StringUtils.trimToEmpty(line);
                if (trimmed.startsWith("UNB")) {
                    return trimmed;
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting UNB line: {}", e.getMessage());
        }
        return null;
    }
}
