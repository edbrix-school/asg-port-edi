package com.asg.portediintegration.service;

import com.asg.portediintegration.entity.EdiContainerGateInOut;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class EdiParserServiceImpl implements EdiParserService {

    private static final DateTimeFormatter EDI_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyyHHmm");

    /**
     * Parse CODECO EDI message and extract container movement data
     * This processes the main cursor C_EDI which contains BGM, EQD, DTM, UNB, RFF, NAD+CF+, TDT+20+
     */
    public EdiContainerGateInOut parseCodecoMessage(String[] ediLines, String ediRefNo, String fileName) {
        EdiContainerGateInOut gateRecord = new EdiContainerGateInOut();
        gateRecord.setEdiLoadDate(LocalDateTime.now());
        gateRecord.setCreatedDate(LocalDateTime.now());
        // Convert String ediRefNo to Long for EdiContainerGateInOut (if column is NUMBER)
        try {
            gateRecord.setEdiRefNo(Long.parseLong(ediRefNo));
        } catch (NumberFormatException e) {
            log.warn("Could not parse ediRefNo as Long: {}", ediRefNo);
            // If conversion fails, try to extract numeric part or use 0
            String numericPart = ediRefNo.replaceAll("[^0-9]", "");
            if (!numericPart.isEmpty()) {
                gateRecord.setEdiRefNo(Long.parseLong(numericPart));
            } else {
                gateRecord.setEdiRefNo(0L);
            }
        }

        String gateType = null;
        String containerNo = null;
        String transactionType = null;
        String lineCode = null;
        String bookingNo = "NOT PRESENT";
        String moveDate = null;
        String ediFileDate = null;
        String vessalVoyage = null;

        for (String line : ediLines) {
            if (line.startsWith("UNB")) {
                // Extract EDI file date - format: DDMMRRRRHH24MI
                ediFileDate = extractEdiFileDate();
            } else if (line.startsWith("BGM+34")) { // Gate In
                gateType = "34";
            } else if (line.startsWith("BGM+36")) { // Gate Out
                gateType = "36";
            } else if (line.startsWith("BGM+999")) { // Gate Strip/Stuff
                gateType = "999";
            } else if (line.startsWith("EQD+CN")) {
                // Extract container number and transaction type
                containerNo = extractContainerNo(line);
                transactionType = extractTransactionType(line);
            } else if (line.startsWith("RFF+BN")) {
                // Extract booking number
                bookingNo = extractBookingNo(line);
            } else if (line.startsWith("NAD+CF+")) {
                // Extract line code
                lineCode = extractLineCode(line);
            } else if (line.startsWith("TDT+20+")) {
                // Extract vessel voyage
                vessalVoyage = line;
            } else if (line.startsWith("DTM+7")) {
                // Extract movement date
                moveDate = extractMoveDate(line, fileName);
            }
        }

        // Set basic fields
        gateRecord.setContainerNo(containerNo);
        gateRecord.setLineCode(lineCode);
        gateRecord.setBookingNo(bookingNo);
        gateRecord.setVessalVoyage(vessalVoyage);

        if (ediFileDate != null) {
            try {
                gateRecord.setEdiFileDate(LocalDateTime.parse(ediFileDate, EDI_DATE_FORMAT));
            } catch (Exception e) {
                log.warn("Failed to parse EDI file date: {}", ediFileDate);
                gateRecord.setEdiFileDate(LocalDateTime.now());
            }
        } else {
            gateRecord.setEdiFileDate(LocalDateTime.now());
        }

        // Process movement based on gate type and transaction type
        if (moveDate != null && containerNo != null) {
            LocalDateTime movementDateTime = parseEdiDateTime(moveDate);
            processMovement(gateRecord, gateType, transactionType, movementDateTime, bookingNo, fileName);
        }

        return gateRecord;
    }

    /**
     * Process additional EDI segments (LOC, SEL, MEA, NAD+CZ) for specific movement types
     * This is equivalent to cursor C_EDI_LOC in the procedure
     */
    public void processAdditionalSegments(EdiContainerGateInOut gateRecord, List<String> ediLines,
                                          Long currentSeqNo, String fileName) {
        String podest = null;
        String podisch = null;
        String sealNo = "XXX";
        String grossWeight = null;
        String vgmWeight = null;
        String title = null;

        // Process lines after current sequence number until next BGM
        for (String line : ediLines) {
            if (line.startsWith("BGM")) {
                // Stop processing when we hit next BGM segment
                break;
            }

            if (line.startsWith("LOC+8+")) {
                // Extract PODEST (Port of Destination)
                podest = extractLocationCode(line);
            } else if (line.startsWith("LOC+11+")) {
                // Extract PODISCH (Port of Discharge)
                podisch = extractLocationCode(line);
            } else if (line.startsWith("SEL") && !line.startsWith("SEL+N")) {
                // Extract seal number
                sealNo = extractSealNo(line);
            } else if (line.startsWith("MEA+AAE+G+")) {
                // Extract gross weight
                grossWeight = extractWeight(line);
            } else if (line.startsWith("MEA+AAE+VGM+")) {
                // Extract VGM weight
                vgmWeight = extractWeight(line);
            } else if (line.startsWith("NAD+CZ+")) {
                // Extract title
                title = extractTitle(line);
            }
        }

        // Set additional fields
        if (podest != null) {
            gateRecord.setPodest(podest);
        }
        if (podisch != null) {
            gateRecord.setPodisch(podisch);
        }
        if (sealNo != null && !"XXX".equals(sealNo)) {
            gateRecord.setSealNo(sealNo);
        }
        if (grossWeight != null) {
            gateRecord.setGrossWeight(grossWeight);
        }
        if (vgmWeight != null) {
            gateRecord.setVgmWeight(vgmWeight);
        }
        if (title != null) {
            gateRecord.setTitle(title);
        }
    }

    /**
     * Process container movement based on gate type and transaction type
     */
    private void processMovement(EdiContainerGateInOut gateRecord, String gateType, String transactionType,
                                 LocalDateTime moveDateTime, String bookingNo, String fileName) {

        String upperFileName = fileName.toUpperCase();

        // Import Gate Out Full: BGM+36 AND (TRNTYP LIKE '3+%5' OR ((TRNTYP LIKE '%9+5' OR TRNTYP LIKE '%++5') AND P_BOOK_NO= 'NOT PRESENT' AND upper(P_FILE_NAME) like '%RCL%'))
        if ("36".equals(gateType) &&
                (transactionType != null && (transactionType.contains("3+5") ||
                        ((transactionType.contains("9+5") || transactionType.contains("++5")) &&
                                "NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL"))))) {
            gateRecord.setImportGateOutFull(moveDateTime);

            // Empty Gate In: BGM+34 AND (TRNTYP LIKE '%+%4' OR TRNTYP LIKE '%2+%4') AND TRNTYP not LIKE '%9+4'
        } else if ("34".equals(gateType) && transactionType != null &&
                (transactionType.contains("+4") || transactionType.contains("2+4")) &&
                !transactionType.contains("9+4")) {
            gateRecord.setEmptyGateIn(moveDateTime);

            // Empty Date Out: BGM+36 AND (TRNTYP LIKE '%+%4' OR TRNTYP LIKE '%2+%4') AND TRNTYP not LIKE '%9+5'
        } else if ("36".equals(gateType) && transactionType != null &&
                (transactionType.contains("+4") || transactionType.contains("2+4")) &&
                !transactionType.contains("9+5")) {
            gateRecord.setEmptyDateOut(moveDateTime);

            // Export Date In Full: BGM+34 AND (TRNTYP LIKE '2+%5' OR ((TRNTYP LIKE '%9+5' OR TRNTYP LIKE '%++5') AND P_BOOK_NO<> 'NOT PRESENT' AND upper(P_FILE_NAME) like '%RCL%'))
        } else if ("34".equals(gateType) && transactionType != null &&
                ("2+5".equals(transactionType) ||
                        ((transactionType.contains("9+5") || transactionType.contains("++5")) &&
                                !"NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL")))) {
            gateRecord.setExportDateInFull(moveDateTime);

            // Stripping Import: (BGM+999 AND TRNTYP LIKE '%+%4') OR (BGM+34 AND TRNTYP LIKE '%9+4')
        } else if (("999".equals(gateType) && transactionType != null && transactionType.contains("+4")) ||
                ("34".equals(gateType) && transactionType != null && transactionType.contains("9+4"))) {
            gateRecord.setStipingImport(moveDateTime);

            // Stuffing Export: (BGM+999 AND TRNTYP LIKE '2+%5') OR (BGM+36 AND TRNTYP LIKE '%9+5' AND P_BOOK_NO<> 'NOT PRESENT' AND upper(P_FILE_NAME) like '%RCL%')
        } else if (("999".equals(gateType) && transactionType != null && "2+5".equals(transactionType)) ||
                ("36".equals(gateType) && transactionType != null && transactionType.contains("9+5") &&
                        !"NOT PRESENT".equals(bookingNo) && upperFileName.contains("RCL"))) {
            gateRecord.setStuffingExport(moveDateTime);
        }
    }

    /**
     * Extract movement date from DTM segment
     * Format: DTM+7:RRMMDDHHMI or DTM+7:RRMMDDHHMI:203
     * Procedure uses: SUBSTR(P_EDI.TAB_TEXT,13,2)||SUBSTR(P_EDI.TAB_TEXT,11,2)||SUBSTR(P_EDI.TAB_TEXT,7,4)||SUBSTR(P_EDI.TAB_TEXT,15,4)
     * This means: day(13,2) + month(11,2) + year(7,4) + time(15,4) = DDMMRRRRHHMI
     */
    private String extractMoveDate(String line, String fileName) {
        try {
            // Remove "DTM+7:" prefix
            String dateStr = line.substring(6);

            // Remove any suffix after the date (like :203)
            int colonIndex = dateStr.indexOf(':', 1);
            if (colonIndex > 0) {
                dateStr = dateStr.substring(0, colonIndex);
            }

            // Remove quotes if present
            dateStr = dateStr.replace("'", "");

            if (dateStr.length() >= 12) {
                // Format: RRMMDDHHMI -> DDMMRRRRHHMI
                // Position 7-10: year (RRRR)
                // Position 11-12: month (MM)
                // Position 13-14: day (DD)
                // Position 15-18: time (HHMI)
                String year = dateStr.substring(6, 10);
                String month = dateStr.substring(10, 12);
                String day = dateStr.substring(12, 14);
                String time = dateStr.length() >= 18 ? dateStr.substring(14, 18) : "0000";
                return day + month + year + time;
            } else if (dateStr.length() >= 10) {
                // Shorter format: RRMMDDHHMI
                String year = "20" + dateStr.substring(0, 2);
                String month = dateStr.substring(2, 4);
                String day = dateStr.substring(4, 6);
                String time = dateStr.substring(6, 10);
                return day + month + year + time;
            }
        } catch (Exception e) {
            log.warn("Failed to extract move date from line: {}", line);
        }
        return null;
    }

    /**
     * Extract EDI file date from UNB segment
     */
    private String extractEdiFileDate() {
        // Format: DDMMRRRRHH24MI
        LocalDateTime now = LocalDateTime.now();
        return String.format("%02d%02d%04d%02d%02d",
                now.getDayOfMonth(), now.getMonthValue(), now.getYear(),
                now.getHour(), now.getMinute());
    }

    /**
     * Extract container number from EQD+CN segment
     */
    private String extractContainerNo(String line) {
        try {
            // Format: EQD+CN+CONTAINER_NO+...
            int firstPlus = line.indexOf('+', 4); // After "EQD+CN+"
            if (firstPlus > 0) {
                int secondPlus = line.indexOf('+', firstPlus + 1);
                if (secondPlus > 0) {
                    return line.substring(firstPlus + 1, secondPlus);
                } else {
                    // No second plus, take until colon or quote
                    int colon = line.indexOf(':', firstPlus + 1);
                    int quote = line.indexOf("'", firstPlus + 1);
                    int end = Math.min(colon > 0 ? colon : Integer.MAX_VALUE,
                            quote > 0 ? quote : Integer.MAX_VALUE);
                    if (end < Integer.MAX_VALUE) {
                        return line.substring(firstPlus + 1, end);
                    } else {
                        return line.substring(firstPlus + 1);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract container number from: {}", line);
        }
        return null;
    }

    /**
     * Extract transaction type from EQD segment (last 3 characters)
     */
    private String extractTransactionType(String line) {
        if (line.length() >= 3) {
            return line.substring(line.length() - 3);
        }
        return null;
    }

    /**
     * Extract booking number from RFF+BN segment
     */
    private String extractBookingNo(String line) {
        try {
            // Format: RFF+BN:BOOKING_NO'
            String value = line.substring(7); // After "RFF+BN"
            value = value.replace("'", "");
            if (value.contains(":")) {
                return value.substring(value.indexOf(':') + 1);
            }
            return value;
        } catch (Exception e) {
            log.warn("Failed to extract booking number from: {}", line);
        }
        return "NOT PRESENT";
    }

    /**
     * Extract line code from NAD+CF+ segment
     */
    private String extractLineCode(String line) {
        try {
            // Format: NAD+CF+LINE_CODE:172:20'
            int firstPlus = line.indexOf('+', 4); // After "NAD+CF+"
            if (firstPlus > 0) {
                int colon = line.indexOf(':', firstPlus + 1);
                if (colon > 0) {
                    return line.substring(firstPlus + 1, colon);
                } else {
                    int quote = line.indexOf("'", firstPlus + 1);
                    if (quote > 0) {
                        return line.substring(firstPlus + 1, quote);
                    }
                    return line.substring(firstPlus + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract line code from: {}", line);
        }
        return null;
    }

    /**
     * Extract location code from LOC segment
     */
    private String extractLocationCode(String line) {
        try {
            // Format: LOC+8+PORT_CODE:139:6' or LOC+11+PORT_CODE:139:6'
            int firstPlus = line.indexOf('+', 4); // After "LOC+X+"
            if (firstPlus > 0) {
                int secondPlus = line.indexOf('+', firstPlus + 1);
                if (secondPlus > 0) {
                    int colon = line.indexOf(':', secondPlus + 1);
                    if (colon > 0) {
                        return line.substring(secondPlus + 1, colon);
                    } else {
                        int quote = line.indexOf("'", secondPlus + 1);
                        if (quote > 0) {
                            return line.substring(secondPlus + 1, quote);
                        }
                        return line.substring(secondPlus + 1);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract location code from: {}", line);
        }
        return null;
    }

    /**
     * Extract seal number from SEL segment
     */
    private String extractSealNo(String line) {
        try {
            // Format: SEL+SEAL_NO+...
            int firstPlus = line.indexOf('+');
            if (firstPlus > 0) {
                int secondPlus = line.indexOf('+', firstPlus + 1);
                if (secondPlus > 0) {
                    return line.substring(firstPlus + 1, secondPlus);
                } else {
                    int quote = line.indexOf("'", firstPlus + 1);
                    if (quote > 0) {
                        return line.substring(firstPlus + 1, quote);
                    }
                    return line.substring(firstPlus + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract seal number from: {}", line);
        }
        return "XXX";
    }

    /**
     * Extract weight from MEA segment
     */
    private String extractWeight(String line) {
        try {
            // Format: MEA+AAE+G+:WEIGHT' or MEA+AAE+VGM+:WEIGHT'
            int lastPlus = line.lastIndexOf('+');
            if (lastPlus > 0) {
                String value = line.substring(lastPlus + 1);
                value = value.replace("'", "");
                if (value.contains(":")) {
                    return value.substring(value.indexOf(':') + 1);
                }
                return value;
            }
        } catch (Exception e) {
            log.warn("Failed to extract weight from: {}", line);
        }
        return null;
    }

    /**
     * Extract title from NAD+CZ+ segment
     */
    private String extractTitle(String line) {
        try {
            // Format: NAD+CZ+TITLE'
            int firstPlus = line.indexOf('+', 4); // After "NAD+CZ+"
            if (firstPlus > 0) {
                int secondPlus = line.indexOf('+', firstPlus + 1);
                if (secondPlus > 0) {
                    String value = line.substring(firstPlus + 1, secondPlus);
                    return value.replace("'", "");
                } else {
                    String value = line.substring(firstPlus + 1);
                    return value.replace("'", "");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract title from: {}", line);
        }
        return null;
    }

    /**
     * Parse EDI date time format to LocalDateTime
     */
    private LocalDateTime parseEdiDateTime(String ediDateTime) {
        try {
            if (ediDateTime != null && ediDateTime.length() >= 12) {
                return LocalDateTime.parse(ediDateTime, EDI_DATE_FORMAT);
            }
        } catch (Exception e) {
            log.warn("Failed to parse EDI date time: {}", ediDateTime);
        }
        return LocalDateTime.now();
    }

    /**
     * Extract value from EDI segment, removing quotes and extra characters
     */
    private String extractValue(String segment) {
        return segment.replace("'", "").split(":")[0];
    }
}
