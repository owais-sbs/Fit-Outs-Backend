package com.fitouts.drawing.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.error.BadRequestException;
import com.fitouts.storage.config.S3StorageProperties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Slf4j
public class FileStorageService {

  private final Path root;
  private final S3StorageProperties s3Properties;
  private final S3Client s3Client;

  public FileStorageService(
      @Value("${fitouts.upload.dir:uploads}") String uploadDir,
      S3StorageProperties s3Properties,
      @Autowired(required = false) S3Client s3Client) {
    this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    this.s3Properties = s3Properties;
    this.s3Client = s3Client;
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new RuntimeException("Could not create upload directory", e);
    }
    if (s3Enabled()) {
      log.info("S3 storage enabled for bucket {}", s3Properties.getBucket());
    }
  }

  public boolean isS3Enabled() {
    return s3Enabled();
  }

  public String publicUrl(String relativePath) {
    if (!s3Enabled() || relativePath == null || relativePath.isBlank()) {
      return null;
    }
    String base = s3Properties.getPublicBaseUrl();
    if (base == null || base.isBlank()) {
      return null;
    }
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    return base + normalizeKey(relativePath);
  }

  public String store(MultipartFile file, String subfolder) {
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }
    String original = file.getOriginalFilename();
    if (original == null || original.isBlank()) {
      throw new BadRequestException("File name is required");
    }
    String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
    String unique = UUID.randomUUID() + "_" + safeName;
    Path targetDir = root.resolve(subfolder);
    try {
      Files.createDirectories(targetDir);
      Path target = targetDir.resolve(unique);
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      String relativePath = root.relativize(target).toString().replace('\\', '/');
      uploadToS3(relativePath, target);
      return relativePath;
    } catch (IOException e) {
      throw new RuntimeException("Failed to store file", e);
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
      String relativePath = root.relativize(target).toString().replace('\\', '/');
      uploadToS3(relativePath, target);
      return relativePath;
    } catch (IOException e) {
      throw new RuntimeException("Failed to store file", e);
    }
  }

  public Path resolve(String relativePath) {
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      throw new BadRequestException("Invalid file path");
    }
    if (!Files.exists(resolved)) {
      downloadFromS3IfPresent(relativePath, resolved);
    }
    return resolved;
  }

  public Resource loadAsResource(String relativePath) {
    Path file = resolve(relativePath);
    if (Files.exists(file)) {
      return toUrlResource(file);
    }
    if (s3Enabled() && existsOnS3(relativePath)) {
      try {
        InputStream stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(s3Key(relativePath))
                        .build());
        return new InputStreamResource(stream);
      } catch (S3Exception e) {
        throw new BadRequestException("File not found");
      }
    }
    throw new BadRequestException("File not found");
  }

  public String toRelativePath(Path absolute) {
    String relativePath = root.relativize(absolute.normalize()).toString().replace('\\', '/');
    uploadToS3(relativePath, absolute);
    return relativePath;
  }

  public void deleteIfExists(String relativePath) {
    if (relativePath == null) {
      return;
    }
    try {
      Files.deleteIfExists(resolve(relativePath));
    } catch (IOException ignored) {
    }
    if (s3Enabled()) {
      try {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(s3Key(relativePath))
                .build());
      } catch (S3Exception e) {
        log.warn("Failed to delete S3 object {}: {}", relativePath, e.getMessage());
      }
    }
  }

  private boolean s3Enabled() {
    return s3Properties.isEnabled()
            && s3Client != null
            && s3Properties.getBucket() != null
            && !s3Properties.getBucket().isBlank()
            && s3Properties.getAccessKey() != null
            && !s3Properties.getAccessKey().isBlank()
            && s3Properties.getSecretKey() != null
            && !s3Properties.getSecretKey().isBlank();
  }

  private String normalizeKey(String relativePath) {
    return relativePath.replace('\\', '/').replaceAll("^/+", "");
  }

  private String s3Key(String relativePath) {
    String key = normalizeKey(relativePath);
    String prefix = Optional.ofNullable(s3Properties.getKeyPrefix()).orElse("").trim();
    if (prefix.isEmpty()) {
      return key;
    }
    prefix = prefix.replaceAll("^/+|/+$", "");
    return prefix + "/" + key;
  }

  private void uploadToS3(String relativePath, Path localFile) {
    if (!s3Enabled() || !Files.exists(localFile)) {
      return;
    }
    try {
      String contentType = Files.probeContentType(localFile);
      if (contentType == null || contentType.isBlank()) {
        contentType = guessContentType(localFile.getFileName().toString());
      }
      s3Client.putObject(
              PutObjectRequest.builder()
                      .bucket(s3Properties.getBucket())
                      .key(s3Key(relativePath))
                      .contentType(contentType)
                      .build(),
              RequestBody.fromFile(localFile));
      log.debug("Uploaded to S3: {}", s3Key(relativePath));
    } catch (IOException | S3Exception e) {
      String awsError = formatS3Error(e);
      if (s3Properties.isFailOnUploadError()) {
        throw new RuntimeException("Failed to upload file to S3: " + relativePath + " — " + awsError, e);
      }
      log.warn(
              "S3 upload failed for {} (saved locally at {}): {}",
              s3Key(relativePath),
              relativePath,
              awsError);
    }
  }

  private String formatS3Error(Exception e) {
    if (e instanceof S3Exception s3) {
      String code = s3.awsErrorDetails() != null ? s3.awsErrorDetails().errorCode() : null;
      String message = s3.awsErrorDetails() != null ? s3.awsErrorDetails().errorMessage() : s3.getMessage();
      return (code != null ? code + " — " : "") + message;
    }
    return e.getMessage();
  }

  private String guessContentType(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".doc")) return "application/msword";
    if (lower.endsWith(".docx")) {
      return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }
    if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
    if (lower.endsWith(".xlsx")) {
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    return "application/octet-stream";
  }

  private void downloadFromS3IfPresent(String relativePath, Path target) {
    if (!s3Enabled() || !existsOnS3(relativePath)) {
      return;
    }
    try {
      Files.createDirectories(target.getParent());
      s3Client.getObject(
              GetObjectRequest.builder()
                      .bucket(s3Properties.getBucket())
                      .key(s3Key(relativePath))
                      .build(),
              target);
    } catch (S3Exception | IOException e) {
      throw new RuntimeException("Failed to download file from S3: " + relativePath, e);
    }
  }

  private boolean existsOnS3(String relativePath) {
    try {
      s3Client.headObject(HeadObjectRequest.builder()
              .bucket(s3Properties.getBucket())
              .key(s3Key(relativePath))
              .build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  private Resource toUrlResource(Path file) {
    try {
      Resource resource = new UrlResource(file.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new BadRequestException("File not found");
      }
      return resource;
    } catch (IOException e) {
      throw new BadRequestException("File not found");
    }
  }
}
