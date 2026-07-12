package com.fitouts.drawing.application;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.kabeja.parser.Parser;
import org.kabeja.parser.ParserBuilder;
import org.kabeja.processing.ProcessPipeline;
import org.kabeja.processing.ProcessingManager;
import org.kabeja.processing.ProcessorException;
import org.kabeja.svg.SVGGenerator;
import org.kabeja.xml.SAXPrettyOutputter;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DxfPreviewService {

  private final FileStorageService fileStorageService;

  public String convertToPreview(Path dxfFile) {
    String baseName = stripExtension(dxfFile.getFileName().toString());
    Path svgFile = dxfFile.getParent().resolve(baseName + ".svg");
    if (runSvgPipeline(dxfFile, svgFile)) {
      return fileStorageService.toRelativePath(svgFile);
    }
    log.warn("DXF preview generation failed for {}", dxfFile);
    return null;
  }

  private boolean runSvgPipeline(Path dxfFile, Path svgFile) {
    try {
      ProcessingManager manager = new ProcessingManager();
      manager.addSAXGenerator(new SVGGenerator(), "svg");
      manager.addSAXSerializer(new SAXPrettyOutputter(), "svg");

      ProcessPipeline pipeline = new ProcessPipeline();
      pipeline.setName("svg");
      pipeline.setProcessorManager(manager);
      pipeline.setSAXGenerator(manager.getSAXGenerator("svg"));
      pipeline.setSAXSerializer(manager.getSAXSerializer("svg"));
      pipeline.prepare();

      try (InputStream in = Files.newInputStream(dxfFile);
          OutputStream out = Files.newOutputStream(svgFile)) {
        Parser parser = ParserBuilder.createDefaultParser();
        parser.parse(in, "UTF-8");
        pipeline.process(parser.getDocument(), Collections.emptyMap(), out);
      }

      if (!Files.exists(svgFile) || Files.size(svgFile) == 0) {
        log.warn("DXF→SVG produced empty output for {}", dxfFile);
        deleteQuietly(svgFile);
        return false;
      }
      return true;
    } catch (ProcessorException e) {
      log.warn("DXF→SVG failed for {}: {}", dxfFile, e.getMessage());
      deleteQuietly(svgFile);
      return false;
    } catch (Exception e) {
      log.warn("DXF→SVG error for {}: {}", dxfFile, e.toString(), e);
      deleteQuietly(svgFile);
      return false;
    }
  }

  private void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (Exception ignored) {
      // best effort cleanup
    }
  }

  private static String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
