package com.asg.portediintegration.controller;

import com.asg.portediintegration.service.EdiOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.asg.common.lib.dto.response.ApiResponse.success;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/edi")
public class EdiFileController {

    private final EdiOrchestrationService ediOrchestrationService;


    /**
     * Manually trigger EDI file processing from unread messages
     */
    @PostMapping("/process")
    public ResponseEntity<?> processEdiFiles() {
        log.info("Manual trigger: Processing EDI files from Outlook inbox");

        List<Path> processedFiles = ediOrchestrationService.execute();

        Map<String, Object> data = new HashMap<>();
        data.put("filesProcessed", processedFiles.size());
        data.put("filePaths", processedFiles.stream()
                .map(Path::toString)
                .toList());

        return success("EDI files processed successfully", data);
    }

    /**
     * Process EDI files from all messages (read and unread)
     */
    @PostMapping("/process/all")
    public ResponseEntity<?> processAllEdiFiles() {
        log.info("Manual trigger: Processing EDI files from all Outlook inbox messages");

        List<Path> processedFiles = ediOrchestrationService.executeAll();

        Map<String, Object> data = new HashMap<>();
        data.put("filesProcessed", processedFiles.size());
        data.put("filePaths", processedFiles.stream()
                .map(Path::toString)
                .toList());

        return success("EDI files processed successfully", data);
    }
}

