package com.asg.portediintegration.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface EdiFileService {
    Path saveEdiFile(String fileName, InputStream inputStream) throws IOException;

    Path moveEdiFile(Path sourcePath, String fileName) throws IOException;
}