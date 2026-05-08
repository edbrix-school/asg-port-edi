package com.asg.portediintegration.controller;

import com.asg.portediintegration.service.GlobalParameterService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import com.asg.portediintegration.service.EdiFileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final EdiFileService ediFileService;
    private final GlobalParameterService globalParameterService;

    private static final String TARGET_FOLDER_KEY = "PORT_EDI_FOLDER";


    private String getTargetFolder() {
        String folder = globalParameterService.getLinuxAwareValue(TARGET_FOLDER_KEY);
        if (StringUtils.isBlank(folder)) {
            throw new IllegalStateException("Target folder not configured in GLOBAL_PARAMETERS: " + TARGET_FOLDER_KEY);
        }
        return folder;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "EDI Email Processor");

        // Get target folder dynamically from EdiFileService
        String targetFolder = getTargetFolder();

        // Check if target folder exists
        boolean folderExists = Files.exists(Paths.get(targetFolder));
        health.put("targetFolder", targetFolder);
        health.put("targetFolderExists", folderExists);

        if (folderExists) {
            Path folderPath = Paths.get(targetFolder);
            try (Stream<Path> stream = Files.list(folderPath)) {
                long fileCount = stream.filter(Files::isRegularFile).count();
                health.put("filesInTargetFolder", fileCount);
            } catch (Exception e) {
                health.put("filesInTargetFolder", "Error reading folder: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(health);
    }
}
