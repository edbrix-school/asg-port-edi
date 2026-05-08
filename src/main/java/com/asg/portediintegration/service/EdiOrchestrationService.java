package com.asg.portediintegration.service;

import java.nio.file.Path;
import java.util.List;

public interface EdiOrchestrationService {
    List<Path> execute();

    List<Path> executeAll();
}
