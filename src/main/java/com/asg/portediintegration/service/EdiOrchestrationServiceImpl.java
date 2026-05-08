package com.asg.portediintegration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdiOrchestrationServiceImpl implements EdiOrchestrationService {

    private final EdiFileProcessorService ediFileProcessorService; // email → folder
    private final EdiProcessingService ediProcessingService;       // folder → DB

    @Override
    public List<Path> execute() {
        log.info("Step 1: Fetching EDI files from email inbox");
        List<Path> processedFiles = ediFileProcessorService.processEdiFilesFromInbox();
        processEdiFiles();
        return processedFiles;
    }

    @Override
    public List<Path> executeAll() {
        log.info("Step 1: Fetching EDI files from all Outlook inbox messages");
        List<Path> processedFiles = ediFileProcessorService.processEdiFilesFromAllMessages();
        processEdiFiles();
        return processedFiles;
    }

    private void processEdiFiles() {
        log.info("Step 2: Processing EDI files from inbound folder");
        ediProcessingService.processEdiFiles();
    }
}

