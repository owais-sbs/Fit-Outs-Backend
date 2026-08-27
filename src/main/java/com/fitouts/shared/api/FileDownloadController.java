package com.fitouts.shared.api;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.drawing.application.FileStorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileDownloadController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{*relativePath}")
    public ResponseEntity<Resource> download(@PathVariable("relativePath") String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        Resource resource = fileStorageService.loadAsResource(path);
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(resolveMediaType(filename))
                .body(resource);
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (lower.endsWith(".mov")) return MediaType.parseMediaType("video/quicktime");
        if (lower.endsWith(".m4v")) return MediaType.parseMediaType("video/x-m4v");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
