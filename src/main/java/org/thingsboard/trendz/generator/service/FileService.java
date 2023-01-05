package org.thingsboard.trendz.generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class FileService {

    private final String userDir;
    private final String prefix;


    public FileService() {
        this.userDir = System.getProperty("user.dir");
        this.prefix = "/src/main/resources/files";
    }

    public String getFileContent(String solutionName, String fileName) throws IOException {
        Path path = Path.of(userDir, this.prefix, solutionName, fileName);
        return Files.readString(path);
    }
}
