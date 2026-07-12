package com.fitouts.drawing.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.error.BadRequestException;

@Service
public class FileStorageService {

  private final Path root;

  public FileStorageService(@Value("${fitouts.upload.dir:uploads}") String uploadDir) {
    this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new RuntimeException("Could not create upload directory", e);
    }
  }

  public String store(MultipartFile file, UUID companyId, Long projectId, String subfolder) {
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }
    String original = file.getOriginalFilename();
    if (original == null || original.isBlank()) {
      throw new BadRequestException("File name is required");
    }
    String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
    String unique = UUID.randomUUID() + "_" + safeName;
    Path targetDir = root.resolve(companyId.toString()).resolve(String.valueOf(projectId)).resolve(subfolder);
    try {
      Files.createDirectories(targetDir);
      Path target = targetDir.resolve(unique);
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return root.relativize(target).toString().replace('\\', '/');
    } catch (IOException e) {
      throw new RuntimeException("Failed to store file", e);
    }
  }

  public Path resolve(String relativePath) {
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      throw new BadRequestException("Invalid file path");
    }
    return resolved;
  }

  public Resource loadAsResource(String relativePath) {
    try {
      Path file = resolve(relativePath);
      Resource resource = new UrlResource(file.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new BadRequestException("File not found");
      }
      return resource;
    } catch (IOException e) {
      throw new BadRequestException("File not found");
    }
  }

  public String toRelativePath(Path absolute) {
    return root.relativize(absolute.normalize()).toString().replace('\\', '/');
  }

  public void deleteIfExists(String relativePath) {
    if (relativePath == null) return;
    try {
      Files.deleteIfExists(resolve(relativePath));
    } catch (IOException ignored) {
    }
  }
}
