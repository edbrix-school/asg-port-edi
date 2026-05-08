package com.asg.portediintegration.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive EDI file validation utility
 * Validates EDI file structure, format, schema compliance, and calculates checksums
 */
@Slf4j
public class EdiFileValidator {

    // Constants
    private static final long DEFAULT_MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB (default fallback)
    private static final Pattern CONTAINER_NUMBER_PATTERN = Pattern.compile("^[A-Z]{4}\\d{6}[0-9A-Z]$");
    private static final Pattern BOOKING_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{6,20}$");

    // Required segments for CODECO messages
    private static final Set<String> CODECO_REQUIRED_SEGMENTS = Set.of("UNB", "UNH", "BGM", "UNT", "UNZ");

    // Required segments for COARRI messages
    private static final Set<String> COARRI_REQUIRED_SEGMENTS = Set.of("UNB", "UNH", "BGM", "UNT", "UNZ");

    // EDI segment delimiters
    private static final char ELEMENT_SEPARATOR = '+';
    private static final char COMPONENT_SEPARATOR = ':';
    private static final char SEGMENT_TERMINATOR = '\'';

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final String checksum;
        private final String messageType;
        private final int messageCount;
        private final Map<String, Object> metadata;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings,
                                String checksum, String messageType, int messageCount,
                                Map<String, Object> metadata) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.checksum = checksum;
            this.messageType = messageType;
            this.messageCount = messageCount;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

        public static ValidationResult success(String checksum, String messageType,
                                               int messageCount, Map<String, Object> metadata) {
            return new ValidationResult(true, new ArrayList<>(), new ArrayList<>(),
                    checksum, messageType, messageCount, metadata);
        }

        public static ValidationResult failure(List<String> errors, List<String> warnings,
                                               String checksum, String messageType) {
            return new ValidationResult(false, errors, warnings, checksum, messageType, 0, new HashMap<>());
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getChecksum() {
            return checksum;
        }

        public String getMessageType() {
            return messageType;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }

    /**
     * Validates an EDI file from InputStream
     *
     * @param inputStream      The EDI file input stream
     * @param fileName         File name for logging
     * @param maxFileSizeBytes Maximum allowed file size in bytes (null to use default)
     * @return ValidationResult with validation status, errors, warnings, and checksum
     */
    public static ValidationResult validateEdiFile(InputStream inputStream, String fileName, Long maxFileSizeBytes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String checksum = null;
        String messageType = null;
        int messageCount = 0;
        Map<String, Object> metadata = new HashMap<>();

        try {
            // Step 1: File size validation
            long maxSize = maxFileSizeBytes != null ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE;
            if (!validateFileSize(inputStream, fileName, maxSize, errors)) {
                return ValidationResult.failure(errors, warnings, null, null);
            }

            // Step 2: Read file content and calculate checksum
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = inputStream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
            }

            byte[] fileContent = baos.toByteArray();
            checksum = ChecksumUtils.calculateSHA256(new ByteArrayInputStream(fileContent), null);
            metadata.put("fileSize", fileContent.length);
            metadata.put("checksum", checksum);

            // Step 3: Read file as text (EDI standard uses ISO-8859-1 or UTF-8)
            String content = new String(fileContent, StandardCharsets.ISO_8859_1);
            List<String> lines = Arrays.asList(content.split("\r?\n"));

            // Step 4: Basic file structure validation
            if (!validateBasicStructure(lines, fileName, errors, warnings)) {
                return ValidationResult.failure(errors, warnings, checksum, null);
            }

            // Step 5: Detect message type
            messageType = detectMessageType(lines);
            if (messageType == null) {
                errors.add("Unable to detect EDI message type (CODECO or COARRI)");
                return ValidationResult.failure(errors, warnings, checksum, null);
            }
            metadata.put("messageType", messageType);

            // Step 6: Validate interchange structure (UNB/UNZ pairs)
            if (!validateInterchangeStructure(lines, errors, warnings)) {
                return ValidationResult.failure(errors, warnings, checksum, messageType);
            }

            // Step 7: Validate message structure (UNH/UNT pairs)
            messageCount = validateMessageStructure(lines, messageType, errors, warnings);
            if (messageCount == 0) {
                return ValidationResult.failure(errors, warnings, checksum, messageType);
            }
            metadata.put("messageCount", messageCount);

            // Step 8: Validate segment syntax
            validateSegmentSyntax(lines, errors, warnings);

            // Step 9: Validate required segments
            validateRequiredSegments(lines, messageType, errors, warnings);

            // Step 10: Validate data elements
            validateDataElements(lines, messageType, errors, warnings);

            // Step 11: Extract metadata
            extractMetadata(lines, messageType, metadata);

            if (errors.isEmpty()) {
                log.info("EDI file validation successful: {} (Type: {}, Messages: {}, Checksum: {})",
                        fileName, messageType, messageCount, checksum);
                return ValidationResult.success(checksum, messageType, messageCount, metadata);
            } else {
                log.warn("EDI file validation failed: {} - Errors: {}", fileName, errors);
                return ValidationResult.failure(errors, warnings, checksum, messageType);
            }

        } catch (IOException e) {
            log.error("Error reading EDI file: {}", fileName, e);
            errors.add("Error reading file: " + e.getMessage());
            return ValidationResult.failure(errors, warnings, checksum, messageType);
        } catch (Exception e) {
            log.error("Unexpected error validating EDI file: {}", fileName, e);
            errors.add("Unexpected validation error: " + e.getMessage());
            return ValidationResult.failure(errors, warnings, checksum, messageType);
        }
    }

    /**
     * Validates file size
     */
    private static boolean validateFileSize(InputStream inputStream, String fileName,
                                            long maxFileSizeBytes, List<String> errors) throws IOException {
        long size = inputStream.available();
        if (size > maxFileSizeBytes) {
            // Clear error message for file size exceeded
            String maxSizeMB = String.format("%.2f", maxFileSizeBytes / (1024.0 * 1024.0));
            String actualSizeMB = String.format("%.2f", size / (1024.0 * 1024.0));
            errors.add(String.format("File size (%d bytes, %s MB) exceeds maximum allowed size (%d bytes, %s MB)",
                    size, actualSizeMB, maxFileSizeBytes, maxSizeMB));
            return false;
        }
        if (size == 0) {
            errors.add("File is empty");
            return false;
        }
        return true;
    }

    /**
     * Validates basic file structure (header signatures)
     */
    private static boolean validateBasicStructure(List<String> lines, String fileName,
                                                  List<String> errors, List<String> warnings) {
        if (lines.isEmpty()) {
            errors.add("File is empty or unreadable");
            return false;
        }

        // Check for UNB (Interchange header) - should be first line
        boolean hasUnb = false;
        for (String line : lines) {
            if (line.trim().startsWith("UNB")) {
                hasUnb = true;
                break;
            }
        }

        if (!hasUnb) {
            errors.add("File does not contain UNB (Interchange header) segment");
            return false;
        }

        // Check file extension
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".edi") && !lowerName.endsWith(".txt")) {
            warnings.add("File extension is not .edi or .txt");
        }

        return true;
    }

    /**
     * Detects EDI message type (CODECO or COARRI)
     */
    private static String detectMessageType(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("UNH")) {
                if (line.contains("CODECO")) {
                    return "CODECO";
                } else if (line.contains("COARRI")) {
                    return "COARRI";
                }
            }
        }
        return null;
    }

    /**
     * Validates interchange structure (UNB/UNZ pairs)
     */
    private static boolean validateInterchangeStructure(List<String> lines,
                                                        List<String> errors, List<String> warnings) {
        int unbCount = 0;
        int unzCount = 0;
        String unbControlNumber = null;
        String unzControlNumber = null;

        for (String line : lines) {
            if (line.startsWith("UNB")) {
                unbCount++;
                // Extract control number from UNB (last element before segment terminator)
                String[] parts = line.split("\\+");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    unbControlNumber = lastPart.replace("'", "").trim();
                }
            } else if (line.startsWith("UNZ")) {
                unzCount++;
                // Extract control number from UNZ
                String[] parts = line.split("\\+");
                if (parts.length >= 2) {
                    unzControlNumber = parts[1].replace("'", "").trim();
                }
            }
        }

        if (unbCount == 0) {
            errors.add("Missing UNB (Interchange header) segment");
            return false;
        }
        if (unbCount > 1) {
            errors.add(String.format("Multiple UNB segments found: %d (expected 1)", unbCount));
            return false;
        }
        if (unzCount == 0) {
            errors.add("Missing UNZ (Interchange trailer) segment");
            return false;
        }
        if (unzCount > 1) {
            errors.add(String.format("Multiple UNZ segments found: %d (expected 1)", unzCount));
            return false;
        }

        // Validate control numbers match
        if (unbControlNumber != null && unzControlNumber != null &&
                !unbControlNumber.equals(unzControlNumber)) {
            warnings.add(String.format("UNB control number (%s) does not match UNZ control number (%s)",
                    unbControlNumber, unzControlNumber));
        }

        return true;
    }

    /**
     * Validates message structure (UNH/UNT pairs) and returns message count
     */
    private static int validateMessageStructure(List<String> lines, String messageType,
                                                List<String> errors, List<String> warnings) {
        List<String> unhSegments = new ArrayList<>();
        List<String> untSegments = new ArrayList<>();
        Map<String, Integer> messageRefs = new HashMap<>();

        for (String line : lines) {
            if (line.startsWith("UNH")) {
                unhSegments.add(line);
                // Extract message reference
                String[] parts = line.split("\\+");
                if (parts.length > 1) {
                    String msgRef = parts[1].replace("'", "").trim();
                    messageRefs.put(msgRef, messageRefs.getOrDefault(msgRef, 0) + 1);
                }
            } else if (line.startsWith("UNT")) {
                untSegments.add(line);
            }
        }

        if (unhSegments.size() != untSegments.size()) {
            errors.add(String.format("Mismatch in message structure: %d UNH segments but %d UNT segments",
                    unhSegments.size(), untSegments.size()));
            return 0;
        }

        // Validate each UNH/UNT pair
        int messageIndex = 0;
        int segmentCount = 0;
        boolean inMessage = false;
        String currentMsgRef = null;

        for (String line : lines) {
            if (line.startsWith("UNH")) {
                if (inMessage) {
                    errors.add("UNH segment found before previous message was closed with UNT");
                }
                inMessage = true;
                segmentCount = 1; // Count UNH itself
                String[] parts = line.split("\\+");
                if (parts.length > 1) {
                    currentMsgRef = parts[1].replace("'", "").trim();
                }
            } else if (line.startsWith("UNT")) {
                if (!inMessage) {
                    errors.add("UNT segment found without corresponding UNH");
                } else {
                    // Validate segment count in UNT
                    String[] parts = line.split("\\+");
                    if (parts.length >= 1) {
                        try {
                            String segmentCountStr = parts[0].substring(3); // Remove "UNT" prefix
                            int expectedCount = Integer.parseInt(segmentCountStr.trim());
                            if (expectedCount != segmentCount) {
                                warnings.add(String.format("Message %d: Segment count mismatch - UNT reports %d but actual count is %d",
                                        messageIndex + 1, expectedCount, segmentCount));
                            }
                        } catch (NumberFormatException e) {
                            warnings.add("Unable to parse segment count from UNT segment");
                        }
                    }
                    // Validate message reference matches
                    if (parts.length >= 2 && currentMsgRef != null) {
                        String untMsgRef = parts[1].replace("'", "").trim();
                        if (!currentMsgRef.equals(untMsgRef)) {
                            errors.add(String.format("Message reference mismatch: UNH has %s but UNT has %s",
                                    currentMsgRef, untMsgRef));
                        }
                    }
                    inMessage = false;
                    messageIndex++;
                }
            } else if (inMessage) {
                segmentCount++;
            }
        }

        if (inMessage) {
            errors.add("File ends with unclosed message (missing UNT segment)");
            return 0;
        }

        return unhSegments.size();
    }

    /**
     * Validates segment syntax
     */
    private static void validateSegmentSyntax(List<String> lines, List<String> errors, List<String> warnings) {
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Check segment terminator
            if (!trimmed.endsWith(String.valueOf(SEGMENT_TERMINATOR))) {
                errors.add(String.format("Line %d: Segment does not end with segment terminator (')", lineNumber));
            }

            // Check segment tag (first 3 characters should be uppercase letters)
            if (trimmed.length() < 3) {
                errors.add(String.format("Line %d: Segment too short (minimum 3 characters for tag)", lineNumber));
            } else {
                String tag = trimmed.substring(0, 3);
                if (!tag.matches("^[A-Z]{3}$")) {
                    errors.add(String.format("Line %d: Invalid segment tag format: %s (must be 3 uppercase letters)",
                            lineNumber, tag));
                }
            }
        }
    }

    /**
     * Validates required segments are present
     */
    private static void validateRequiredSegments(List<String> lines, String messageType,
                                                 List<String> errors, List<String> warnings) {
        Set<String> requiredSegments = messageType.equals("CODECO") ?
                CODECO_REQUIRED_SEGMENTS : COARRI_REQUIRED_SEGMENTS;

        Set<String> foundSegments = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= 3) {
                String tag = trimmed.substring(0, 3);
                foundSegments.add(tag);
            }
        }

        Set<String> missing = new HashSet<>(requiredSegments);
        missing.removeAll(foundSegments);

        if (!missing.isEmpty()) {
            errors.add(String.format("Missing required segments for %s: %s", messageType, missing));
        }
    }

    /**
     * Validates data elements (container numbers, dates, etc.)
     */
    private static void validateDataElements(List<String> lines, String messageType,
                                             List<String> errors, List<String> warnings) {
        for (String line : lines) {
            // Validate container numbers in EQD+CN segments
            if (line.startsWith("EQD+CN")) {
                String containerNo = extractContainerNumber(line);
                if (containerNo != null && !validateContainerNumber(containerNo)) {
                    warnings.add(String.format("Invalid container number format: %s (expected ISO 6346 format: 4 letters + 6 digits + check digit)",
                            containerNo));
                }
            }

            // Validate booking numbers in RFF+BN segments
            if (line.startsWith("RFF+BN")) {
                String bookingNo = extractBookingNumber(line);
                if (bookingNo != null && !validateBookingNumber(bookingNo)) {
                    warnings.add(String.format("Invalid booking number format: %s", bookingNo));
                }
            }

            // Validate date formats in DTM segments
            if (line.startsWith("DTM+7")) {
                String dateStr = extractDateFromDtm(line);
                if (dateStr != null && !validateDateFormat(dateStr)) {
                    warnings.add(String.format("Invalid date format in DTM+7 segment: %s", dateStr));
                }
            }
        }
    }

    /**
     * Extracts container number from EQD+CN segment
     */
    private static String extractContainerNumber(String line) {
        try {
            // Format: EQD+CN+CONTAINER_NO+...
            String[] parts = line.split("\\+");
            if (parts.length >= 3) {
                return parts[2].split(":")[0].replace("'", "").trim();
            }
        } catch (Exception e) {
            log.debug("Error extracting container number: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validates container number format (ISO 6346)
     */
    private static boolean validateContainerNumber(String containerNo) {
        if (containerNo == null || containerNo.length() != 11) {
            return false;
        }
        return CONTAINER_NUMBER_PATTERN.matcher(containerNo).matches();
    }

    /**
     * Extracts booking number from RFF+BN segment
     */
    private static String extractBookingNumber(String line) {
        try {
            // Format: RFF+BN:BOOKING_NO'
            if (line.contains(":")) {
                String afterColon = line.substring(line.indexOf(':') + 1);
                return afterColon.replace("'", "").trim();
            }
        } catch (Exception e) {
            log.debug("Error extracting booking number: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validates booking number format
     */
    private static boolean validateBookingNumber(String bookingNo) {
        if (bookingNo == null || bookingNo.isEmpty() || bookingNo.equals("NOT PRESENT")) {
            return true; // Optional field
        }
        return BOOKING_NUMBER_PATTERN.matcher(bookingNo).matches();
    }

    /**
     * Extracts date from DTM segment
     */
    private static String extractDateFromDtm(String line) {
        try {
            // Format: DTM+7:DATE:203'
            if (line.contains(":")) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    return parts[1].replace("'", "").trim();
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting date: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validates date format (should be numeric, typically YYYYMMDDHHMI or similar)
     */
    private static boolean validateDateFormat(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return false;
        }
        // EDI dates are typically 8-14 digits
        return dateStr.matches("^\\d{8,14}$");
    }

    /**
     * Extracts metadata from EDI file
     */
    private static void extractMetadata(List<String> lines, String messageType, Map<String, Object> metadata) {
        for (String line : lines) {
            if (line.startsWith("UNB")) {
                // Extract sender, receiver, date from UNB
                String[] parts = line.split("\\+");
                if (parts.length >= 4) {
                    metadata.put("sender", parts[2].trim());
                    metadata.put("receiver", parts[3].trim());
                    if (parts.length >= 5) {
                        String dateTime = parts[4].split(":")[0];
                        metadata.put("interchangeDate", dateTime);
                    }
                }
            } else if (line.startsWith("BGM")) {
                // Extract BGM code (34, 36, 98, 270, 999)
                String[] parts = line.split("\\+");
                if (parts.length >= 2) {
                    metadata.put("bgmCode", parts[1].trim());
                }
            }
        }

        // Count containers
        long containerCount = lines.stream()
                .filter(l -> l.startsWith("EQD+CN"))
                .count();
        metadata.put("containerCount", containerCount);
    }

    /**
     * Validates EDI file from file path
     */
    public static ValidationResult validateEdiFile(Path filePath, Long maxFileSizeBytes) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return validateEdiFile(inputStream, filePath.getFileName().toString(), maxFileSizeBytes);
        }
    }

    /**
     * Validates an EDI file from InputStream (overload with default max file size)
     *
     * @param inputStream The EDI file input stream
     * @param fileName    File name for logging
     * @return ValidationResult with validation status, errors, warnings, and checksum
     */
    public static ValidationResult validateEdiFile(InputStream inputStream, String fileName) {
        return validateEdiFile(inputStream, fileName, null);
    }
}
