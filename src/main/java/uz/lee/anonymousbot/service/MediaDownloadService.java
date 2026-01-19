package uz.lee.anonymousbot.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaDownloadService {

    private final Path downloadDirectory;

    public MediaDownloadService(@Value("${media.download.directory:downloads}") String downloadDirectory) throws IOException {
        this.downloadDirectory = Paths.get(downloadDirectory);
        Files.createDirectories(this.downloadDirectory);
    }

    public Path download(String url, MediaType type) throws IOException, InterruptedException {
        String baseName = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String outputTemplate = downloadDirectory.resolve(baseName + ".%(ext)s").toString();
        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("--no-playlist");
        command.add("--print");
        command.add("filename");
        command.add("-o");
        command.add(outputTemplate);
        if (type == MediaType.AUDIO) {
            command.add("-x");
            command.add("--audio-format");
            command.add("mp3");
        } else {
            command.add("-f");
            command.add("best");
        }
        command.add(url);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("yt-dlp xatolik bilan tugadi: " + output);
        }

        String filename = output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IOException("Fayl nomi topilmadi."));
        Path filePath = Paths.get(filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Yuklangan fayl mavjud emas: " + filePath);
        }
        return filePath;
    }
}
