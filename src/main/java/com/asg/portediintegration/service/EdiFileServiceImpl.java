package com.asg.portediintegration.service;

import com.asg.portediintegration.utils.ChecksumUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdiFileServiceImpl implements EdiFileService {

    private final GlobalParameterService globalParameterService;

    private static final String TARGET_FOLDER_KEY = "PORT_EDI_FOLDER";

    private String targetFolder;

    @PostConstruct
    private void init() {
        this.targetFolder = getTargetFolderFromDb();
        initializeTargetFolder();
    }

    private String getTargetFolderFromDb() {
        String folder = globalParameterService.getLinuxAwareValue(TARGET_FOLDER_KEY);
        if (StringUtils.isBlank(folder)) {
            throw new IllegalStateException("Target folder is not configured in GLOBAL_PARAMETERS: " + TARGET_FOLDER_KEY);
        }
        return folder;
    }

    /**
     * Initializes the target folder if it doesn't exist
     */
    private void initializeTargetFolder() {
        try {
            Path targetPath = Paths.get(targetFolder);
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
                log.info("Created target folder: {}", targetFolder);
            } else {
                log.info("Target folder already exists: {}", targetFolder);
            }
        } catch (IOException e) {
            log.error("Error creating target folder: {}", targetFolder, e);
            throw new RuntimeException("Failed to create target folder", e);
        }
    }

    /**
     * Saves EDI file to target folder
     */
    public Path saveEdiFile(String fileName, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
        String safeName = StringUtils.defaultString(fileName, "unnamed");

        // Ensure target folder exists
        Path folderPath = Paths.get(targetFolder);
        if (Files.notExists(folderPath)) {
            Files.createDirectories(folderPath);
            log.info("Created target folder: {}", folderPath);
        }

        // Create unique filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseName = getBaseFileName(safeName);
        String extension = getFileExtension(safeName);
        String uniqueFileName = String.format("%s_%s%s", baseName, timestamp, extension);

        Path targetPath = folderPath.resolve(uniqueFileName);

        try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW)) {
            String checksumHex = ChecksumUtils.calculateSHA256(in, out);
            log.info("Saved EDI file: {} to {} with SHA-256 checksum: {}", uniqueFileName, targetPath, checksumHex);

//            byte[] buffer = new byte[8192]; // 8 KB buffer
//            int bytesRead;
//
//            while ((bytesRead = in.read(buffer)) != -1) {
//                out.write(buffer, 0, bytesRead);
//            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        log.info("Saved EDI file: {} to {}", uniqueFileName, targetPath);
        return targetPath;
    }


    /**
     * Moves EDI file to target folder (if file already exists on filesystem)
     */
    public Path moveEdiFile(Path sourcePath, String fileName) throws IOException {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String baseName = getBaseFileName(fileName);
            String extension = getFileExtension(fileName);
            String uniqueFileName = String.format("%s_%s%s", baseName, timestamp, extension);

            Path targetPath = Paths.get(targetFolder, uniqueFileName);
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Moved EDI file: {} to {}", fileName, targetPath);
            return targetPath;
        } catch (IOException e) {
            log.error("Error moving EDI file: {}", fileName, e);
            throw e;
        }
    }

    /**
     * Gets base file name without extension
     */
    private String getBaseFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "unnamed";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    /**
     * Gets file extension
     */
    private String getFileExtension(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }

    public void reloadTargetFolder() {
        this.targetFolder = getTargetFolderFromDb();
        initializeTargetFolder();
        log.info("Target folder reloaded: {}", this.targetFolder);
    }
}