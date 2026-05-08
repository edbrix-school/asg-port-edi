package com.asg.portediintegration.service;

import com.asg.portediintegration.entity.EdiContainerGateInOut;
import com.asg.portediintegration.entity.EdiUploadCodeco;
import com.asg.portediintegration.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdiProcessingServiceImpl implements EdiProcessingService {

    private final EdiContainerGateInOutRepository gateInOutRepository;
    private final EdiUploadCodecoRepository uploadCodecoRepository;
    private final EdiParserService ediParserService;
    private final ShipBlManifestContainerDtlRepository manifestContainerDtlRepository;
    private final ShipMateContainerDtlRepository mateContainerDtlRepository;
    private final ShipLineMasterRepository lineMasterRepository;
    private final ShipContainerInventoryRepository containerInventoryRepository;
    private final GlobalDebugLogRecordsRepository debugLogRepository;
    private final GlobalParameterService globalParameterService;
    // TODO: Inject email notification and virus scan services
    // private final EmailNotificationService emailNotificationService;
    // private final VirusScanService virusScanService;

    private static final String TARGET_FOLDER_KEY = "PORT_EDI_FOLDER";
    private static final String SUCCESS_FOLDER_KEY = "PORT_EDI_FOLDER_SUCCESS";
    private static final String ERROR_FOLDER_KEY = "PORT_EDI_FOLDER_ERROR";


    private String getTargetFolder() {
        String folder = globalParameterService.getLinuxAwareValue(TARGET_FOLDER_KEY);
        if (StringUtils.isBlank(folder)) {
            throw new IllegalStateException("Target folder not configured in GLOBAL_PARAMETERS: " + TARGET_FOLDER_KEY);
        }
        return folder;
    }

    private String getSuccessFolder() {
        String folder = globalParameterService.getLinuxAwareValue(SUCCESS_FOLDER_KEY);
        if (StringUtils.isBlank(folder)) {
            throw new IllegalStateException("Success folder not configured in GLOBAL_PARAMETERS: " + SUCCESS_FOLDER_KEY);
        }
        return folder;
    }

    private String getErrorFolder() {
        String folder = globalParameterService.getLinuxAwareValue(ERROR_FOLDER_KEY);
        if (StringUtils.isBlank(folder)) {
            throw new IllegalStateException("Error folder not configured in GLOBAL_PARAMETERS: " + ERROR_FOLDER_KEY);
        }
        return folder;
    }


    /**
     * Main EDI processing method - equivalent to PROC_PORT_EDI_LOAD_BATCH
     */
    public void processEdiFiles() {
        log.info("Starting EDI file processing batch");

        try {
            // Step 1: Process COPEOR validity mail (with exception handling)
            try {
                procCopeorValidityMail();
            } catch (Exception e) {
                log.warn("Error in proc_copeor_VALIDITY_MAIL: {}", e.getMessage());
                // Continue processing even if this fails
            }

            // Step 2: Process EDI files from target folder
            portEdiReadTransfer();

            // Step 3: Insert records into database
            portEdiInsertRecords();

            // Step 4: Clean up cross trade and shipper own containers
            cleanupCrossTrade();

            log.info("EDI file processing batch completed successfully");
        } catch (Exception e) {
            log.error("Error during EDI file processing: {}", e.getMessage(), e);
            throw new RuntimeException("EDI processing failed", e);
        }
    }

    /**
     * Equivalent to proc_copeor_VALIDITY_MAIL procedure
     * Note: This is a complex procedure that generates COPEOR validity EDI files.
     * Implementation depends on specific business requirements.
     */
    private void procCopeorValidityMail() {
        log.info("Processing COPEOR validity mail");
        // TODO: Implement COPEOR validity mail generation if needed
        // This procedure generates COPEOR EDI files for containers with validity dates
        // It involves complex queries and file generation logic
        // For now, this is a placeholder that can be implemented based on business needs
    }

    /**
     * Equivalent to PORT_EDI_READ_TRANSFER procedure
     */
    private void portEdiReadTransfer() {
        log.info("Starting EDI file transfer and processing");

        try {
            File targetDir = new File(getTargetFolder());
            if (!targetDir.exists()) {
                log.warn("EDI target folder does not exist: {}", getTargetFolder());
                return;
            }

            File[] files = targetDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".edi") || name.toLowerCase().endsWith(".txt"));

            if (files == null || files.length == 0) {
                log.info("No EDI files found in target folder");
                return;
            }

            for (File file : files) {
                if (file.getName().equals(".working")) {
                    continue;
                }

                log.info("Processing EDI file: {}", file.getName());

                try {
                    boolean success = processEdiFile(file);

                    // If CODECO processing failed, try COARRI processing
                    if (!success) {
                        log.info("CODECO processing failed, trying COARRI processing for: {}", file.getName());
                        success = processDischargeLoadFile(file);
                    }

                    moveFile(file, success);
                } catch (Exception e) {
                    log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
                    moveFile(file, false);
                }
            }
        } catch (Exception e) {
            log.error("Error in EDI file transfer: {}", e.getMessage(), e);
            throw new RuntimeException("EDI file transfer failed", e);
        }
    }

    /**
     * Process individual EDI file - equivalent to PORT_EDI_CODECO_UPLOAD
     */
    private boolean processEdiFile(File file) {
        // ============================================
        // TODO: VIRUS SCAN - Scan file before processing
        // ============================================
        // if (virusScanService.isScanningEnabled()) {
        //     VirusScanService.ScanResult scanResult = virusScanService.scanFile(
        //             Files.newInputStream(file.toPath()), file.getName());
        //     if (!scanResult.isClean()) {
        //         emailNotificationService.sendVirusDetectionNotification(
        //                 file.getName(), scanResult.getThreatName(), scanResult.getDetails());
        //         return false;
        //     }
        // }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String ediRefNo = getNextEdiRefNo();
            Long seqnoFileLine = 0L;
            Long mainGroupNo = 0L;
            Long slotNumber = 0L;

            String line;
            boolean isCodecoFile = false;

            while ((line = reader.readLine()) != null) {
                seqnoFileLine++;

                if (line.startsWith("UNA")) {
                    mainGroupNo++;
                }

                if (line.startsWith("UNH")) {
                    if (!line.contains("CODECO")) {
                        log.warn("File {} is not a CODECO file", file.getName());
                        insertDebugLog("port_EDI_CODECO_UPLOAD", "1",
                                "NOT A CODECO FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                                "NOT A CODECO FILE-OTHER FILE ", LocalDateTime.now());
                        // EMAIL NOTIFICATION: Validation failure - not a CODECO file
                        // emailNotificationService.sendValidationFailureNotification(
                        //         file.getName(), 
                        //         List.of("File is not a CODECO file"), 
                        //         List.of(), 
                        //         null);
                        return false;
                    }
                    isCodecoFile = true;
                }

                if (line.startsWith("UNB")) {
                    // Check if file already processed
                    List<EdiUploadCodeco> existing = uploadCodecoRepository
                            .findByTabTextAndFileLoadName(line, "CODECO");
                    if (!existing.isEmpty()) {
                        log.warn("File {} already processed", file.getName());
                        insertDebugLog("port_EDI_CODECO_UPLOAD", "2",
                                "ALREADY LOADED CODECO FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                                "ALREDY LOADED CODECO-CODECO", LocalDateTime.now());
                        return false;
                    }
                    slotNumber++;
                }

                // Save EDI line to database
                EdiUploadCodeco ediRecord = new EdiUploadCodeco();
                ediRecord.setEdiRefNo(ediRefNo);
                ediRecord.setSeqnoFileLine(seqnoFileLine);
                ediRecord.setMainGroupNo(mainGroupNo);
                ediRecord.setSlotNumber(slotNumber);
                ediRecord.setTabText(line);
                ediRecord.setFileLoadName("CODECO");
                ediRecord.setEdiLoadDate(LocalDateTime.now());
                ediRecord.setEdiLoadFlag("N");

                uploadCodecoRepository.save(ediRecord);
            }

            if (!isCodecoFile) {
                log.warn("File {} is not a valid CODECO file", file.getName());
                // EMAIL NOTIFICATION: Validation failure - invalid CODECO file
                // emailNotificationService.sendValidationFailureNotification(
                //         file.getName(), 
                //         List.of("File is not a valid CODECO file"), 
                //         List.of(), 
                //         null);
                return false;
            }

            // Process the uploaded data
            try {
                processCodecoData(ediRefNo, file.getName());
            } catch (Exception e) {
                // EMAIL NOTIFICATION: Parsing/processing failure during data processing
                log.error("Error processing CODECO data for file {}: {}", file.getName(), e.getMessage(), e);
                // emailNotificationService.sendParsingFailureNotification(
                //         file.getName(), 
                //         "Error processing CODECO data: " + e.getMessage(), 
                //         null);
                throw e;
            }

            // Mark as processed - update only records for this EDI_REF_NO
            uploadCodecoRepository.updateEdiLoadFlagByRefNo(ediRefNo, "CODECO");

            insertDebugLog("port_EDI_CODECO_UPLOAD", "3",
                    "CODECO FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                    "SUCESS CODECO-CODECO", LocalDateTime.now());

            log.info("Successfully processed EDI file: {}", file.getName());
            return true;

        } catch (IOException e) {
            log.error("Error reading file {}: {}", file.getName(), e.getMessage(), e);
            insertDebugLog("port_EDI_CODECO_UPLOAD", "4",
                    "CODECO FILE ~~" + file.getAbsolutePath() + "~" + e.getMessage() + "," + file.getAbsolutePath(),
                    "NOT SUCESS CODECO-CODECO", LocalDateTime.now());
            // EMAIL NOTIFICATION: File corruption/IO error
            // emailNotificationService.sendFileCorruptionNotification(
            //         file.getName(), 
            //         "IO error reading file: " + e.getMessage(), 
            //         null);
            return false;
        } catch (Exception e) {
            log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
            insertDebugLog("port_EDI_CODECO_UPLOAD", "4",
                    "CODECO FILE ~~" + file.getAbsolutePath() + "~" + e.getMessage() + "," + file.getAbsolutePath(),
                    "NOT SUCESS CODECO-CODECO", LocalDateTime.now());
            // EMAIL NOTIFICATION: Processing failure
            // emailNotificationService.sendProcessingFailureNotification(
            //         file.getName(), 
            //         "Error processing file: " + e.getMessage(), 
            //         e);
            return false;
        }
    }

    /**
     * Process CODECO data and create container gate records
     * This replicates the logic from PORT_EDI_CODECO_UPLOAD procedure
     */
    private void processCodecoData(String ediRefNo, String fileName) {
        log.info("Processing CODECO data for EDI ref: {}", ediRefNo);

        try {
            // Get all EDI lines for this reference, sorted by sequence
            List<EdiUploadCodeco> allEdiRecords = uploadCodecoRepository
                    .findByEdiRefNoAndFileLoadName(ediRefNo, "CODECO");

            if (allEdiRecords.isEmpty()) {
                log.warn("No EDI records found for ref: {}", ediRefNo);
                return;
            }

            // Filter main cursor C_EDI records (BGM, EQD, DTM, UNB, RFF, NAD+CF+, TDT+20+)
            List<EdiUploadCodeco> mainEdiRecords = allEdiRecords.stream()
                    .filter(e -> {
                        String text = e.getTabText();
                        return (text.startsWith("BGM") || text.startsWith("EQD") ||
                                text.startsWith("DTM") || text.startsWith("UNB") ||
                                text.startsWith("RFF") || text.startsWith("NAD+CF+") ||
                                text.startsWith("TDT+20+")) &&
                                !text.startsWith("DTM+133") && !text.startsWith("DTM+178") &&
                                "N".equals(e.getEdiLoadFlag());
                    })
                    .sorted(Comparator.comparing(EdiUploadCodeco::getSeqnoFileLine)
                            .thenComparing(EdiUploadCodeco::getMainGroupNo)
                            .thenComparing(EdiUploadCodeco::getSlotNumber))
                    .collect(Collectors.toList());

            // Get all EDI lines as strings for additional segment processing
            List<String> allEdiLines = allEdiRecords.stream()
                    .sorted(Comparator.comparing(EdiUploadCodeco::getSeqnoFileLine))
                    .map(EdiUploadCodeco::getTabText)
                    .collect(Collectors.toList());

            // Process records sequentially (like the procedure's cursor loop)
            String gateType = null;
            String containerNo = null;
            String transactionType = null;
            String lineCode = null;
            String bookingNo = "NOT PRESENT";
            String ediFileDate = null;
            String vessalVoyage = null;
            Long currentSeqNo = null;
            Long mainGroupNo = null;
            Long slotNumber = null;

            for (EdiUploadCodeco ediRecord : mainEdiRecords) {
                String line = ediRecord.getTabText();

                if (line.startsWith("UNB")) {
                    ediFileDate = String.format("%02d%02d%04d%02d%02d",
                            LocalDateTime.now().getDayOfMonth(),
                            LocalDateTime.now().getMonthValue(),
                            LocalDateTime.now().getYear(),
                            LocalDateTime.now().getHour(),
                            LocalDateTime.now().getMinute());
                }

                if (line.startsWith("BGM+34")) {
                    gateType = "34";
                } else if (line.startsWith("BGM+36")) {
                    gateType = "36";
                } else if (line.startsWith("BGM+999")) {
                    gateType = "999";
                }

                if (line.startsWith("EQD+CN")) {
                    containerNo = extractContainerNoFromLine(line);
                    transactionType = line.length() >= 3 ? line.substring(line.length() - 3) : null;
                    bookingNo = "NOT PRESENT"; // Reset booking number for new container
                }

                if (line.startsWith("RFF+BN")) {
                    bookingNo = extractBookingNoFromLine(line);
                }

                if (line.startsWith("TDT+20+")) {
                    vessalVoyage = line;
                }

                if (line.startsWith("NAD+CF+")) {
                    lineCode = extractLineCodeFromLine(line);
                }

                // Process DTM+7 segments (movement dates)
                if (line.startsWith("DTM+7")) {
                    String moveDate = extractMoveDateFromLine(line, fileName);
                    if (moveDate != null && containerNo != null) {
                        LocalDateTime movementDateTime = parseEdiDateTime(moveDate);

                        // Create or update gate record based on gate type and transaction type
                        EdiContainerGateInOut gateRecord = createOrUpdateGateRecord(
                                ediRefNo, containerNo, gateType, transactionType, movementDateTime,
                                bookingNo, lineCode, vessalVoyage, ediFileDate,
                                ediRecord.getSeqnoFileLine(), ediRecord.getMainGroupNo(),
                                ediRecord.getSlotNumber(), fileName, allEdiLines, ediRecord.getSeqnoFileLine());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing CODECO data for ref {}: {}", ediRefNo, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create or update gate record based on movement type
     */
    private EdiContainerGateInOut createOrUpdateGateRecord(String ediRefNo, String containerNo,
                                                           String gateType, String transactionType, LocalDateTime moveDateTime,
                                                           String bookingNo, String lineCode, String vessalVoyage, String ediFileDate,
                                                           Long seqNo, Long mainGroupNo, Long slotNumber, String fileName,
                                                           List<String> allEdiLines, Long currentSeqNo) {

        String upperFileName = fileName.toUpperCase();
        EdiContainerGateInOut gateRecord = null;
        boolean needsAdditionalSegments = false;

        // Determine movement type and create/update record
        if ("36".equals(gateType) && transactionType != null &&
                (transactionType.contains("3+5") ||
                        ((transactionType.contains("9+5") || transactionType.contains("++5")) &&
                                "NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL")))) {
            // Import Gate Out Full
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getImportGateOutFull() == null) {
                gateRecord.setImportGateOutFull(moveDateTime);
            }

        } else if ("34".equals(gateType) && transactionType != null &&
                (transactionType.contains("+4") || transactionType.contains("2+4")) &&
                !transactionType.contains("9+4")) {
            // Empty Gate In
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getEmptyGateIn() == null) {
                gateRecord.setEmptyGateIn(moveDateTime);
            }

        } else if ("36".equals(gateType) && transactionType != null &&
                (transactionType.contains("+4") || transactionType.contains("2+4")) &&
                !transactionType.contains("9+5")) {
            // Empty Date Out - needs additional segments
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getEmptyDateOut() == null) {
                gateRecord.setEmptyDateOut(moveDateTime);
                needsAdditionalSegments = true;
            }

        } else if ("34".equals(gateType) && transactionType != null &&
                ("2+5".equals(transactionType) ||
                        ((transactionType.contains("9+5") || transactionType.contains("++5")) &&
                                !"NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL")))) {
            // Export Date In Full - needs additional segments
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getExportDateInFull() == null) {
                gateRecord.setExportDateInFull(moveDateTime);
                needsAdditionalSegments = true;
            }

        } else if (("999".equals(gateType) && transactionType != null && transactionType.contains("+4")) ||
                ("34".equals(gateType) && transactionType != null && transactionType.contains("9+4"))) {
            // Stripping Import
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getStipingImport() == null) {
                gateRecord.setStipingImport(moveDateTime);
            }

        } else if (("999".equals(gateType) && transactionType != null && "2+5".equals(transactionType)) ||
                ("36".equals(gateType) && transactionType != null && transactionType.contains("9+5") &&
                        !"NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL"))) {
            // Stuffing Export - needs additional segments
            gateRecord = findOrCreateGateRecord(containerNo, lineCode);
            if (gateRecord.getStuffingExport() == null) {
                gateRecord.setStuffingExport(moveDateTime);
                needsAdditionalSegments = true;
            }
        }

        if (gateRecord != null) {
            // Set common fields
            if (gateRecord.getEdiRefNo() == null) {
                // Convert String ediRefNo to Long for EdiContainerGateInOut (if column is NUMBER)
                try {
                    gateRecord.setEdiRefNo(Long.parseLong(ediRefNo));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse ediRefNo as Long: {}", ediRefNo);
                    // If conversion fails, try to extract numeric part or use 0
                    String numericPart = ediRefNo.replaceAll("[^0-9]", "");
                    if (!numericPart.isEmpty()) {
                        gateRecord.setEdiRefNo(Long.parseLong(numericPart));
                    }
                }
            }
            gateRecord.setContainerNo(containerNo);
            gateRecord.setLineCode(lineCode);
            gateRecord.setBookingNo(bookingNo);
            gateRecord.setVessalVoyage(vessalVoyage);
            gateRecord.setEdiLoadDate(LocalDateTime.now());
            gateRecord.setCreatedDate(LocalDateTime.now());
            gateRecord.setSeqnoFileLine(seqNo);
            gateRecord.setMainGroupNo(mainGroupNo);
            gateRecord.setSlotNumber(slotNumber);

            if (ediFileDate != null) {
                try {
                    gateRecord.setEdiFileDate(LocalDateTime.parse(ediFileDate,
                            java.time.format.DateTimeFormatter.ofPattern("ddMMyyyyHHmm")));
                } catch (Exception e) {
                    gateRecord.setEdiFileDate(LocalDateTime.now());
                }
            }

            // Process additional segments if needed
            if (needsAdditionalSegments) {
                ediParserService.processAdditionalSegments(gateRecord, allEdiLines, currentSeqNo, fileName);
            }

            gateInOutRepository.save(gateRecord);
            log.debug("Created/updated gate record for container: {}", containerNo);
        }

        return gateRecord;
    }

    /**
     * Find existing gate record or create new one
     */
    private EdiContainerGateInOut findOrCreateGateRecord(String containerNo, String lineCode) {
        if (containerNo == null || lineCode == null) {
            return new EdiContainerGateInOut();
        }

        List<EdiContainerGateInOut> existing = gateInOutRepository
                .findByContainerNoAndLineCode(containerNo, lineCode);

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        return new EdiContainerGateInOut();
    }

    /**
     * Helper methods to extract values from EDI lines
     */
    private String extractContainerNoFromLine(String line) {
        try {
            int firstPlus = line.indexOf('+', 4);
            if (firstPlus > 0) {
                int secondPlus = line.indexOf('+', firstPlus + 1);
                if (secondPlus > 0) {
                    return line.substring(firstPlus + 1, secondPlus);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract container number");
            // EMAIL NOTIFICATION: Extraction failure
            // emailNotificationService.sendExtractionFailureNotification(
            //         "Unknown", // fileName would need to be passed
            //         "Container Number", 
            //         "Failed to extract container number from line: " + line);
        }
        return null;
    }

    private String extractBookingNoFromLine(String line) {
        try {
            String value = line.substring(7).replace("'", "");
            if (value.contains(":")) {
                return value.substring(value.indexOf(':') + 1);
            }
            return value;
        } catch (Exception e) {
            log.warn("Failed to extract booking number");
            // EMAIL NOTIFICATION: Extraction failure
            // emailNotificationService.sendExtractionFailureNotification(
            //         "Unknown", // fileName would need to be passed
            //         "Booking Number", 
            //         "Failed to extract booking number from line: " + line);
            return "NOT PRESENT";
        }
    }

    private String extractLineCodeFromLine(String line) {
        try {
            int firstPlus = line.indexOf('+', 4);
            if (firstPlus > 0) {
                int colon = line.indexOf(':', firstPlus + 1);
                if (colon > 0) {
                    return line.substring(firstPlus + 1, colon);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract line code");
            // EMAIL NOTIFICATION: Extraction failure
            // emailNotificationService.sendExtractionFailureNotification(
            //         "Unknown", // fileName would need to be passed
            //         "Line Code", 
            //         "Failed to extract line code from line: " + line);
        }
        return null;
    }

    private String extractMoveDateFromLine(String line, String fileName) {
        try {
            String dateStr = line.substring(6);
            int colonIndex = dateStr.indexOf(':', 1);
            if (colonIndex > 0) {
                dateStr = dateStr.substring(0, colonIndex);
            }
            dateStr = dateStr.replace("'", "");

            if (dateStr.length() >= 12) {
                String year = dateStr.substring(6, 10);
                String month = dateStr.substring(10, 12);
                String day = dateStr.substring(12, 14);
                String time = dateStr.length() >= 18 ? dateStr.substring(14, 18) : "0000";
                return day + month + year + time;
            } else if (dateStr.length() >= 10) {
                String year = "20" + dateStr.substring(0, 2);
                String month = dateStr.substring(2, 4);
                String day = dateStr.substring(4, 6);
                String time = dateStr.substring(6, 10);
                return day + month + year + time;
            }
        } catch (Exception e) {
            log.warn("Failed to extract move date");
            // EMAIL NOTIFICATION: Extraction failure
            // emailNotificationService.sendExtractionFailureNotification(
            //         fileName, 
            //         "Move Date", 
            //         "Failed to extract move date from line: " + line);
        }
        return null;
    }

    private LocalDateTime parseEdiDateTime(String ediDateTime) {
        try {
            if (ediDateTime != null && ediDateTime.length() >= 12) {
                return LocalDateTime.parse(ediDateTime,
                        java.time.format.DateTimeFormatter.ofPattern("ddMMyyyyHHmm"));
            }
        } catch (Exception e) {
            log.warn("Failed to parse EDI date time: {}", ediDateTime);
        }
        return LocalDateTime.now();
    }

    /**
     * Update existing gate record with new values
     */
    private void updateExistingGateRecord(EdiContainerGateInOut existing, EdiContainerGateInOut newRecord) {
        if (newRecord.getImportGateOutFull() != null && existing.getImportGateOutFull() == null) {
            existing.setImportGateOutFull(newRecord.getImportGateOutFull());
        }
        if (newRecord.getEmptyGateIn() != null && existing.getEmptyGateIn() == null) {
            existing.setEmptyGateIn(newRecord.getEmptyGateIn());
        }
        if (newRecord.getEmptyDateOut() != null && existing.getEmptyDateOut() == null) {
            existing.setEmptyDateOut(newRecord.getEmptyDateOut());
        }
        if (newRecord.getExportDateInFull() != null && existing.getExportDateInFull() == null) {
            existing.setExportDateInFull(newRecord.getExportDateInFull());
        }
        if (newRecord.getStipingImport() != null && existing.getStipingImport() == null) {
            existing.setStipingImport(newRecord.getStipingImport());
        }
        if (newRecord.getStuffingExport() != null && existing.getStuffingExport() == null) {
            existing.setStuffingExport(newRecord.getStuffingExport());
        }
        if (newRecord.getSealNo() != null && (existing.getSealNo() == null || "XXX".equals(existing.getSealNo()))) {
            existing.setSealNo(newRecord.getSealNo());
        }
        if (newRecord.getGrossWeight() != null) {
            existing.setGrossWeight(newRecord.getGrossWeight());
        }
        if (newRecord.getVgmWeight() != null) {
            existing.setVgmWeight(newRecord.getVgmWeight());
        }
    }

    /**
     * Get EDI lines for a specific reference number
     */
    private List<String> getEdiLinesForRef(String ediRefNo) {
        return uploadCodecoRepository.findByEdiRefNoAndFileLoadName(ediRefNo, "CODECO")
                .stream()
                .sorted(Comparator.comparing(EdiUploadCodeco::getSeqnoFileLine)
                        .thenComparing(EdiUploadCodeco::getMainGroupNo)
                        .thenComparing(EdiUploadCodeco::getSlotNumber))
                .map(EdiUploadCodeco::getTabText)
                .collect(Collectors.toList());
    }

    /**
     * Process COARRI file - equivalent to PORT_EDI_DISCHARGE_LOAD
     */
    private boolean processDischargeLoadFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String ediRefNo = getNextEdiRefNo();
            Long seqnoFileLine = 0L;
            Long mainGroupNo = 0L;
            Long slotNumber = 0L;

            String line;
            boolean isCoarriFile = false;

            while ((line = reader.readLine()) != null) {
                seqnoFileLine++;

                if (line.startsWith("UNA")) {
                    mainGroupNo++;
                }

                if (line.startsWith("UNH")) {
                    if (!line.contains("COARRI")) {
                        log.warn("File {} is not a COARRI file", file.getName());
                        insertDebugLog("port_EDI_DISCHARGE_LOAD", "1",
                                "NOT A COARRI FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                                "NOT A CODECO FILE-OTHER FILE ", LocalDateTime.now());
                        return false;
                    }
                    isCoarriFile = true;
                }

                if (line.startsWith("UNB")) {
                    // Check if file already processed
                    List<EdiUploadCodeco> existing = uploadCodecoRepository
                            .findByTabTextAndFileLoadNameForCoarri(line, "COARRI");
                    if (!existing.isEmpty()) {
                        log.warn("File {} already processed", file.getName());
                        insertDebugLog("port_EDI_DISCHARGE_LOAD", "2",
                                "ALREADY LOADED COARRI FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                                "FAILURE-NO2 ALREDY LOADED COARRI", LocalDateTime.now());
                        return false;
                    }
                    slotNumber++;
                }

                // Save EDI line to database
                EdiUploadCodeco ediRecord = new EdiUploadCodeco();
                ediRecord.setEdiRefNo(ediRefNo);
                ediRecord.setSeqnoFileLine(seqnoFileLine);
                ediRecord.setMainGroupNo(mainGroupNo);
                ediRecord.setSlotNumber(slotNumber);
                ediRecord.setTabText(line);
                ediRecord.setFileLoadName("COARRI");
                ediRecord.setEdiLoadDate(LocalDateTime.now());
                ediRecord.setEdiLoadFlag("N");

                uploadCodecoRepository.save(ediRecord);
            }

            if (!isCoarriFile) {
                log.warn("File {} is not a valid COARRI file", file.getName());
                insertDebugLog("port_EDI_DISCHARGE_LOAD", "3",
                        "NOT LOADED COARRI FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                        "FAILURE-NO3 NO LOADED/DISCHARGE COARRI-COARRI", LocalDateTime.now());
                return false;
            }

            // Mark as processed - update only records for this EDI_REF_NO
            uploadCodecoRepository.updateEdiLoadFlagByRefNo(ediRefNo, "COARRI");

            insertDebugLog("port_EDI_DISCHARGE_LOAD", "4",
                    "COARRI FILE ~~" + file.getAbsolutePath() + "," + file.getAbsolutePath(),
                    "SUCESSED COARRI", LocalDateTime.now());

            log.info("Successfully processed COARRI file: {}", file.getName());
            return true;

        } catch (Exception e) {
            log.error("Error processing COARRI file {}: {}", file.getName(), e.getMessage(), e);
            insertDebugLog("port_EDI_DISCHARGE_LOAD", "5",
                    "COARRI FILE ~~" + file.getAbsolutePath() + "~" + e.getMessage() + "," + file.getAbsolutePath(),
                    "NOT SUCESS COARRI", LocalDateTime.now());
            return false;
        }
    }

    /**
     * Equivalent to PORT_EDI_INSERT_RECORDS procedure
     */
    private void portEdiInsertRecords() {
        log.info("Starting EDI records insertion");

        LocalDateTime fromDate = LocalDateTime.now().minusDays(20);

        // Update container movements for import containers
        // Use native query with MAX() aggregation to match procedure logic
        List<Object[]> containersForUpdate =
                gateInOutRepository.findContainersForUpdateNative(fromDate);

        for (Object[] row : containersForUpdate) {
            // Map Object[] to container data: [CONTAINER_NO, LINECODE, EDI_LOAD_DATE, MAX(IMPORT_GATE_OUT_FULL), MAX(EMPTY_GATE_IN), MAX(STIPING_IMPORT)]
            String containerNo = (String) row[0];
            String lineCode = (String) row[1];
            LocalDateTime ediLoadDate = convertToLocalDateTime(row[2]);
            LocalDateTime importGateOutFull = convertToLocalDateTime(row[3]);
            LocalDateTime emptyGateIn = convertToLocalDateTime(row[4]);
            LocalDateTime stipingImport = convertToLocalDateTime(row[5]);

            updateContainerMovements(containerNo, lineCode, ediLoadDate,
                    importGateOutFull, emptyGateIn, stipingImport);
        }

        // Update MATE records for export containers
        // Use native query with MAX() aggregation to match procedure logic
        List<Object[]> containersForMate =
                gateInOutRepository.findContainersForMateUpdateNative(fromDate);

        for (Object[] row : containersForMate) {
            // Map Object[] to container data: [CONTAINER_NO, LINECODE, EDI_LOAD_DATE, BOOKING_NO, MAX(SEALNO), MAX(GROSS_WEIGHT), MAX(VGM_WEIGHT), MAX(EMPTY_DATE_OUT), MAX(EXPORT_DATE_IN_FULL), MAX(STUFFING_EXPORT)]
            String containerNo = (String) row[0];
            String lineCode = (String) row[1];
            LocalDateTime ediLoadDate = convertToLocalDateTime(row[2]);
            String bookingNo = (String) row[3];
            String sealNo = row[4] != null ? (String) row[4] : "0";
            String grossWeight = row[5] != null ? (String) row[5] : "0";
            String vgmWeight = row[6] != null ? (String) row[6] : "0";
            LocalDateTime emptyDateOut = convertToLocalDateTime(row[7]);
            LocalDateTime exportDateInFull = convertToLocalDateTime(row[8]);
            LocalDateTime stuffingExport = convertToLocalDateTime(row[9]);

            updateMateRecords(containerNo, lineCode, ediLoadDate, bookingNo, sealNo,
                    grossWeight, vgmWeight, emptyDateOut, exportDateInFull, stuffingExport);
        }

        log.info("EDI records insertion completed");
    }

    /**
     * Update container movements in manifest tables
     */
    private void updateContainerMovements(String containerNo, String lineCode, LocalDateTime ediLoadDate,
                                          LocalDateTime importGateOutFull, LocalDateTime emptyGateIn,
                                          LocalDateTime stipingImport) {
        log.debug("Updating container movements for: {}", containerNo);

        try {
            // Get line POID from terminal line code
            List<Long> linePoids = lineMasterRepository
                    .findLinePoidByTerminalLineCode(lineCode);

            if (linePoids.isEmpty()) {
                log.warn("No line POID found for line code: {}", lineCode);
                updateContainerRemarks(containerNo, lineCode, "NOT UPDATED");
                insertDebugLog("port_EDI_INSERT_RECORDS", "1",
                        "CODECO FILE ~~FULL OUT/EMPTY IN~~PROBLEM~~" + containerNo +
                                "~~" + lineCode + "~~FULL OUT/EMPTY IN NOT SUCESSED CODECO",
                        "FULL OUT/EMPTY IN NOT SUCESSED CODECO", LocalDateTime.now());
                return;
            }

            boolean updated = false;
            for (Long linePoid : linePoids) {
                int rowsUpdated = manifestContainerDtlRepository.updateManifestContainerDtl(
                        containerNo,
                        linePoid,
                        importGateOutFull,
                        emptyGateIn,
                        stipingImport
                );

                if (rowsUpdated > 0) {
                    updated = true;
                    break;
                }
            }

            if (updated) {
                updateContainerRemarks(containerNo, lineCode, "UPDATED");
            } else {
                updateContainerRemarks(containerNo, lineCode, "NOT UPDATED");
                insertDebugLog("port_EDI_INSERT_RECORDS", "1",
                        "CODECO FILE ~~FULL OUT/EMPTY IN~~PROBLEM~~" + containerNo +
                                "~~" + lineCode + "~~FULL OUT/EMPTY IN NOT SUCESSED CODECO",
                        "FULL OUT/EMPTY IN NOT SUCESSED CODECO", LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("Error updating container movements for {}: {}",
                    containerNo, e.getMessage(), e);
            updateContainerRemarks(containerNo, lineCode, "NOT UPDATED");
        }
    }

    /**
     * Update MATE records for export containers
     */
    private void updateMateRecords(String containerNo, String lineCode, LocalDateTime ediLoadDate,
                                   String bookingNo, String sealNo, String grossWeight, String vgmWeight,
                                   LocalDateTime emptyDateOut, LocalDateTime exportDateInFull,
                                   LocalDateTime stuffingExport) {
        log.debug("Updating MATE records for: {}", containerNo);

        String selectFlag = "X";

        try {
            // Get line POID from terminal line code
            List<Long> linePoids = lineMasterRepository
                    .findLinePoidByTerminalLineCode(lineCode);

            if (linePoids.isEmpty()) {
                log.warn("No line POID found for line code: {}", lineCode);
                insertDebugLog("port_EDI_INSERT_RECORDS", "2",
                        "CODECO FILE ~~" + selectFlag + "~~MATE PROBLEM~~FLAG-" + selectFlag +
                                "---" + containerNo + "~No line POID found",
                        "MATE NOT SUCESSED CODECO", LocalDateTime.now());
                return;
            }

            for (Long linePoid : linePoids) {
                try {
                    selectFlag = "A";
                    // Find transaction POID by booking number and line POID
                    Long transactionPoid = mateContainerDtlRepository
                            .findTransactionPoidByBookingNoAndLinePoid(bookingNo, linePoid);

                    if (transactionPoid == null) {
                        log.warn("No transaction POID found for booking: {} and line: {}",
                                bookingNo, linePoid);
                        continue;
                    }

                    selectFlag = "B";
                    // Get max DET_ROW_ID
                    Long maxDetRowId = mateContainerDtlRepository.findMaxDetRowId(transactionPoid);
                    Long detRowId = (maxDetRowId != null ? maxDetRowId : 0L) + 1L;

                    // Get equipment ISO type
                    String equipmentIsoType = mateContainerDtlRepository
                            .findEquipmentIsoTypeByContainerNo(containerNo);

                    // Handle empty out moves for export
                    Long oldTransactionPoid = mateContainerDtlRepository
                            .findTransactionPoidByContainerNo(containerNo);

                    LocalDateTime issueToShipper = mateContainerDtlRepository
                            .findMaxEmptyDateOut(containerNo, lineCode);

                    if (oldTransactionPoid != null && oldTransactionPoid != 0) {
                        String inventoryCheck = mateContainerDtlRepository
                                .checkContainerInventoryExists(oldTransactionPoid);

                        if (inventoryCheck == null) {
                            // Update existing MATE container detail
                            mateContainerDtlRepository.updateMateContainerDtlTransaction(
                                    containerNo,
                                    oldTransactionPoid,
                                    transactionPoid,
                                    detRowId
                            );
                        }
                    }

                    // Weight values are already extracted in the native query (after colon)
                    String processedGrossWeight = grossWeight != null ? grossWeight : "0";
                    String processedVgmWeight = vgmWeight != null ? vgmWeight : "0";
                    String processedSealNo = sealNo != null ? sealNo : "0";

                    // Try to update existing record
                    selectFlag = "C";
                    int rowsUpdated = mateContainerDtlRepository.updateMateContainerDtl(
                            transactionPoid,
                            containerNo,
                            emptyDateOut,
                            exportDateInFull,
                            stuffingExport,
                            processedSealNo,
                            processedGrossWeight,
                            processedVgmWeight,
                            equipmentIsoType
                    );

                    if (rowsUpdated == 0) {
                        // Insert new record
                        mateContainerDtlRepository.insertMateContainerDtl(
                                transactionPoid,
                                detRowId,
                                containerNo,
                                processedSealNo,
                                equipmentIsoType,
                                emptyDateOut,
                                exportDateInFull,
                                stuffingExport,
                                processedGrossWeight,
                                processedVgmWeight,
                                issueToShipper
                        );
                    }

                    // Update container remarks
                    updateContainerRemarksForMate(containerNo, lineCode, "UPDATED BOOKING");
                    return; // Success, exit loop

                } catch (Exception e) {
                    log.error("Error updating MATE record for container {}: {}",
                            containerNo, e.getMessage(), e);
                    insertDebugLog("port_EDI_INSERT_RECORDS", "2",
                            "CODECO FILE ~~" + selectFlag + "~~MATE PROBLEM~~FLAG-" + selectFlag +
                                    "---" + containerNo + "~" + e.getMessage(),
                            "MATE NOT SUCESSED CODECO", LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            log.error("Error in updateMateRecords for container {}: {}",
                    containerNo, e.getMessage(), e);
            insertDebugLog("port_EDI_INSERT_RECORDS", "2",
                    "CODECO FILE ~~" + selectFlag + "~~MATE PROBLEM~~FLAG-" + selectFlag +
                            "---" + containerNo + "~" + e.getMessage(),
                    "MATE NOT SUCESSED CODECO", LocalDateTime.now());
        }
    }

    /**
     * Extract weight value from EDI format (e.g., "MEA+AAE+G+:25000" -> "25000")
     */
    private String extractWeightValue(String weightStr) {
        if (weightStr == null || weightStr.isEmpty()) {
            return "0";
        }
        int colonIndex = weightStr.indexOf(':');
        if (colonIndex >= 0 && colonIndex < weightStr.length() - 1) {
            return weightStr.substring(colonIndex + 1);
        }
        return weightStr;
    }

    /**
     * Update container remarks
     */
    private void updateContainerRemarks(String containerNo, String lineCode, String remarks) {
        try {
            gateInOutRepository.updateRemarksForContainer(containerNo, lineCode, remarks);
        } catch (Exception e) {
            log.warn("Error updating remarks for container {}: {}", containerNo, e.getMessage());
            // Fallback to individual update if bulk update fails
            List<EdiContainerGateInOut> containers = gateInOutRepository
                    .findByContainerNoAndLineCode(containerNo, lineCode).stream()
                    .filter(c -> c.getRemarks() == null &&
                            (c.getImportGateOutFull() != null || c.getEmptyGateIn() != null ||
                                    c.getStipingImport() != null))
                    .collect(Collectors.toList());

            for (EdiContainerGateInOut c : containers) {
                c.setRemarks(remarks);
                gateInOutRepository.save(c);
            }
        }
    }

    /**
     * Update container remarks for MATE records
     */
    private void updateContainerRemarksForMate(String containerNo, String lineCode, String remarks) {
        try {
            gateInOutRepository.updateRemarksForMateContainer(containerNo, lineCode, remarks);
        } catch (Exception e) {
            log.warn("Error updating MATE remarks for container {}: {}", containerNo, e.getMessage());
            // Fallback to individual update if bulk update fails
            List<EdiContainerGateInOut> containers = gateInOutRepository
                    .findByContainerNoAndLineCode(containerNo, lineCode).stream()
                    .filter(c -> c.getRemarks() == null &&
                            c.getBookingNo() != null && !c.getBookingNo().equals("NOT PRESENT"))
                    .collect(Collectors.toList());

            for (EdiContainerGateInOut c : containers) {
                c.setRemarks(remarks);
                gateInOutRepository.save(c);
            }
        }
    }

    /**
     * Clean up cross trade and shipper own containers
     */
    private void cleanupCrossTrade() {
        log.info("Cleaning up cross trade and shipper own containers");

        try {
            // Delete shipper own containers
            containerInventoryRepository.deleteShipperOwnContainers();
            log.info("Deleted shipper own containers from inventory");

            // Delete cross trade containers
            containerInventoryRepository.deleteCrossTradeContainers();
            log.info("Deleted cross trade containers from inventory");
        } catch (Exception e) {
            log.error("Error cleaning up containers: {}", e.getMessage(), e);
            // Don't throw exception, just log the error
        }
    }

    /**
     * Get next EDI reference number
     */
    private String getNextEdiRefNo() {
        Long maxRefNo = uploadCodecoRepository.findMaxEdiRefNo();
        long nextRefNo = (maxRefNo != null ? maxRefNo : 0L) + 1L;
        return String.valueOf(nextRefNo);
    }

    /**
     * Move processed file to success or error folder
     */
    private void moveFile(File file, boolean success) {
        try {
            String targetFolder = success ? getSuccessFolder() : getErrorFolder();
            Path targetDir = Paths.get(targetFolder);

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            Path targetPath = targetDir.resolve(file.getName());
            Files.move(file.toPath(), targetPath);

            log.info("Moved file {} to {}", file.getName(), targetFolder);
        } catch (IOException e) {
            log.error("Error moving file {}: {}", file.getName(), e.getMessage(), e);
        }
    }

    /**
     * Insert debug log record
     */
    private void insertDebugLog(String procedureName, String debugValue,
                                String debugRecord, String debugRemarks, LocalDateTime debugDate) {
        try {
            debugLogRepository.insertDebugLog(procedureName, debugValue, debugRecord,
                    debugRemarks, debugDate);
        } catch (Exception e) {
            log.warn("Failed to insert debug log: {}", e.getMessage());
        }
    }

    /**
     * Convert database date/timestamp object to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        try {
            if (dateObj instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) dateObj).toLocalDateTime();
            } else if (dateObj instanceof java.sql.Date) {
                return ((java.sql.Date) dateObj).toLocalDate().atStartOfDay();
            } else if (dateObj instanceof java.util.Date) {
                return new java.sql.Timestamp(((java.util.Date) dateObj).getTime()).toLocalDateTime();
            } else if (dateObj instanceof LocalDateTime) {
                return (LocalDateTime) dateObj;
            }
        } catch (Exception e) {
            log.warn("Failed to convert date object: {}", dateObj);
        }
        return null;
    }
}
