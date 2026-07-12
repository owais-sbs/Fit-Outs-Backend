package com.fitouts.drawing.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DwgConversionService {

  private final FileStorageService fileStorageService;
  private final DxfPreviewService dxfPreviewService;
  private final DxfRasterPreviewService dxfRasterPreviewService;
  private final String converterPath;

  public DwgConversionService(
      FileStorageService fileStorageService,
      DxfPreviewService dxfPreviewService,
      DxfRasterPreviewService dxfRasterPreviewService,
      @Value("${fitouts.dwg.converter.path:}") String converterPath) {
    this.fileStorageService = fileStorageService;
    this.dxfPreviewService = dxfPreviewService;
    this.dxfRasterPreviewService = dxfRasterPreviewService;
    this.converterPath = converterPath == null ? "" : converterPath.trim();
  }

  /**
   * ODA File Converter only supports DWG/DXF output — not PDF.
   * Pipeline: DWG → DXF (ODA) → SVG preview (Kabeja).
   */
  public String convertToPreview(String dwgRelativePath) {
    String dxfRelativePath = convertDwgToDxf(dwgRelativePath);
    if (dxfRelativePath == null) {
      return null;
    }
    Path dxfFile = fileStorageService.resolve(dxfRelativePath);
    String renderedPreview = dxfPreviewService.convertToPreview(dxfFile);
    if (renderedPreview != null) {
      return renderedPreview;
    }

    String rasterPreview = dxfRasterPreviewService.convertToPng(dxfFile);
    if (rasterPreview != null) {
      log.info("SVG preview unavailable; using PNG raster for {}", dwgRelativePath);
      return rasterPreview;
    }

    try {
      long bytes = Files.size(dxfFile);
      if (bytes <= 5L * 1024L * 1024L) {
        log.info("SVG/PNG preview unavailable; using DXF fallback for {}", dwgRelativePath);
        return dxfRelativePath;
      }
      log.warn("DXF preview too large for browser rendering ({} bytes): {}", bytes, dwgRelativePath);
    } catch (IOException e) {
      log.warn("Could not inspect DXF size for {}", dwgRelativePath);
    }
    return null;
  }

  private String convertDwgToDxf(String dwgRelativePath) {
    if (converterPath.isEmpty()) {
      log.warn("DWG converter not configured; set fitouts.dwg.converter.path");
      return null;
    }
    try {
      Path dwgFile = fileStorageService.resolve(dwgRelativePath);
      Path outDir = dwgFile.getParent().resolve("previews");
      Files.createDirectories(outDir);
      String baseName = dwgFile.getFileName().toString();
      int dot = baseName.lastIndexOf('.');
      String dxfName = (dot > 0 ? baseName.substring(0, dot) : baseName) + ".dxf";
      Path dxfFile = outDir.resolve(dxfName);

      ProcessBuilder pb = new ProcessBuilder(
          converterPath,
          dwgFile.getParent().toString(),
          outDir.toString(),
          "ACAD2018",
          "DXF",
          "0",
          "1");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      boolean finished = process.waitFor(3, TimeUnit.MINUTES);
      if (!finished) {
        process.destroyForcibly();
        log.warn("DWG conversion timed out for {}", dwgRelativePath);
        return null;
      }
      String output = new String(process.getInputStream().readAllBytes());
      if (process.exitValue() != 0 || !Files.exists(dxfFile)) {
        log.warn("DWG→DXF failed for {} (exit {}): {}", dwgRelativePath, process.exitValue(), output);
        return null;
      }
      return fileStorageService.toRelativePath(dxfFile);
    } catch (Exception e) {
      log.warn("DWG conversion error: {}", e.getMessage());
      return null;
    }
  }

  public boolean isConverterAvailable() {
    if (converterPath.isEmpty()) return false;
    return Files.isRegularFile(Path.of(converterPath));
  }
}
