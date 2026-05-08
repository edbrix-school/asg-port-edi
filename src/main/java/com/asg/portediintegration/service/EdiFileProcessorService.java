package com.asg.portediintegration.service;

import java.nio.file.Path;
import java.util.List;

public interface EdiFileProcessorService {
    List<Path> processEdiFilesFromInbox();

    List<Path> processEdiFilesFromAllMessages();
}