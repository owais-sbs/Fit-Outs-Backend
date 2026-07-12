package com.fitouts.drawing.application;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DxfRasterPreviewService {

    private static final int MAX_IMAGE_PX = 2400;
    private static final int MAX_SEGMENTS = 2_000_000;

    private final FileStorageService fileStorageService;

    public String convertToPng(Path dxfFile) {
        try {
            ParsedDxf parsed = ParsedDxf.parse(dxfFile);
            if (parsed.segments.isEmpty()) {
                log.warn("DXF raster preview has no drawable geometry: {}", dxfFile);
                return null;
            }

            String baseName = stripExtension(dxfFile.getFileName().toString());
            Path pngFile = dxfFile.getParent().resolve(baseName + ".png");
            renderSegments(parsed.segments, parsed.bounds, pngFile);
            return fileStorageService.toRelativePath(pngFile);
        } catch (Exception e) {
            log.warn("DXF→PNG raster failed for {}: {}", dxfFile, e.toString());
            return null;
        }
    }

    private void renderSegments(List<Segment> segments, Bounds bounds, Path pngFile) throws IOException {
        double width = Math.max(bounds.maxX - bounds.minX, 1.0);
        double height = Math.max(bounds.maxY - bounds.minY, 1.0);
        double scale = Math.min(MAX_IMAGE_PX / width, MAX_IMAGE_PX / height);
        int imgW = Math.max(1, (int) Math.ceil(width * scale));
        int imgH = Math.max(1, (int) Math.ceil(height * scale));

        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imgW, imgH);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));

        AffineTransform tx = new AffineTransform();
        tx.translate(-bounds.minX, bounds.maxY);
        tx.scale(scale, -scale);

        for (Segment segment : segments) {
            if (segment.circle) {
                double diameter = segment.x2 * 2;
                Ellipse2D ellipse = new Ellipse2D.Double(
                        segment.x1 - segment.x2,
                        segment.y1 - segment.x2,
                        diameter,
                        diameter);
                g.draw(tx.createTransformedShape(ellipse));
            } else {
                Line2D line = new Line2D.Double(segment.x1, segment.y1, segment.x2, segment.y2);
                g.draw(tx.createTransformedShape(line));
            }
        }

        g.dispose();
        Files.createDirectories(pngFile.getParent());
        ImageIO.write(image, "png", pngFile.toFile());
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private record Segment(double x1, double y1, double x2, double y2, boolean circle) {
        static Segment line(double x1, double y1, double x2, double y2) {
            return new Segment(x1, y1, x2, y2, false);
        }

        static Segment circle(double cx, double cy, double radius) {
            return new Segment(cx, cy, radius, 0, true);
        }
    }

    private static final class Bounds {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        void include(double x, double y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        void include(Segment segment) {
            if (segment.circle) {
                include(segment.x1 - segment.x2, segment.y1 - segment.x2);
                include(segment.x1 + segment.x2, segment.y1 + segment.x2);
            } else {
                include(segment.x1, segment.y1);
                include(segment.x2, segment.y2);
            }
        }
    }

    private static final class BlockDef {
        final List<Segment> segments = new ArrayList<>();
    }

    private static final class ParsedDxf {
        final List<Segment> segments = new ArrayList<>();
        final Bounds bounds = new Bounds();

        static ParsedDxf parse(Path dxfFile) throws IOException {
            ParsedDxf parsed = new ParsedDxf();
            Map<String, BlockDef> blocks = new HashMap<>();

            String section = null;
            boolean pendingSection = false;
            boolean inBlock = false;
            BlockDef currentBlock = null;
            String entityType = null;
            Map<Integer, String> values = new HashMap<>();

            try (BufferedReader reader = Files.newBufferedReader(dxfFile, StandardCharsets.UTF_8)) {
                String codeLine;
                while ((codeLine = reader.readLine()) != null) {
                    String valueLine = reader.readLine();
                    if (valueLine == null) {
                        break;
                    }
                    int code = Integer.parseInt(codeLine.trim());
                    String value = valueLine.trim();

                    if (pendingSection && code == 2) {
                        section = value;
                        pendingSection = false;
                        continue;
                    }

                    if (code == 0) {
                        if ("SECTION".equals(value)) {
                            pendingSection = true;
                            entityType = null;
                            values.clear();
                            continue;
                        }
                        if ("ENDSEC".equals(value)) {
                            section = null;
                            pendingSection = false;
                            inBlock = false;
                            currentBlock = null;
                            entityType = null;
                            values.clear();
                            continue;
                        }
                        if ("EOF".equals(value)) {
                            break;
                        }

                        if (entityType != null) {
                            flushEntity(section, inBlock, currentBlock, blocks, parsed, entityType, values);
                        }

                        if ("BLOCK".equals(value) && "BLOCKS".equals(section)) {
                            inBlock = true;
                            currentBlock = new BlockDef();
                            entityType = "BLOCK";
                            values.clear();
                            continue;
                        }
                        if ("ENDBLK".equals(value)) {
                            inBlock = false;
                            currentBlock = null;
                            entityType = null;
                            values.clear();
                            continue;
                        }

                        entityType = value;
                        values.clear();
                        continue;
                    }

                    if (code == 2 && "BLOCK".equals(entityType) && inBlock && currentBlock != null) {
                        blocks.put(value.toUpperCase(Locale.ROOT), currentBlock);
                    }

                    if (entityType != null) {
                        values.put(code, value);
                    }
                }
            }

            if (entityType != null) {
                flushEntity(section, inBlock, currentBlock, blocks, parsed, entityType, values);
            }

            if (parsed.bounds.minX == Double.POSITIVE_INFINITY) {
                parsed.bounds.minX = 0;
                parsed.bounds.minY = 0;
                parsed.bounds.maxX = 1;
                parsed.bounds.maxY = 1;
            }

            return parsed;
        }
    }

    private static void flushEntity(
            String section,
            boolean inBlock,
            BlockDef currentBlock,
            Map<String, BlockDef> blocks,
            ParsedDxf parsed,
            String entityType,
            Map<Integer, String> values) {

        if ("BLOCK".equalsIgnoreCase(entityType) || "ENDBLK".equalsIgnoreCase(entityType)) {
            return;
        }

        List<Segment> produced = buildSegments(entityType, values, blocks, new Affine2D());
        if (produced.isEmpty()) {
            return;
        }

        if ("BLOCKS".equals(section) && inBlock && currentBlock != null) {
            currentBlock.segments.addAll(produced);
            return;
        }

        if ("ENTITIES".equals(section) && !inBlock) {
            for (Segment segment : produced) {
                if (parsed.segments.size() >= MAX_SEGMENTS) {
                    return;
                }
                parsed.segments.add(segment);
                parsed.bounds.include(segment);
            }
        }
    }

    private static List<Segment> buildSegments(
            String entityType,
            Map<Integer, String> values,
            Map<String, BlockDef> blocks,
            Affine2D transform) {

        return switch (entityType.toUpperCase(Locale.ROOT)) {
            case "LINE" -> {
                double x1 = transform.x(parseDouble(values.get(10)), parseDouble(values.get(20)));
                double y1 = transform.y(parseDouble(values.get(10)), parseDouble(values.get(20)));
                double x2 = transform.x(parseDouble(values.get(11)), parseDouble(values.get(21)));
                double y2 = transform.y(parseDouble(values.get(11)), parseDouble(values.get(21)));
                yield List.of(Segment.line(x1, y1, x2, y2));
            }
            case "LWPOLYLINE", "POLYLINE" -> buildPolyline(values, transform);
            case "CIRCLE" -> {
                double cx = transform.x(parseDouble(values.get(10)), parseDouble(values.get(20)));
                double cy = transform.y(parseDouble(values.get(10)), parseDouble(values.get(20)));
                double radius = parseDouble(values.get(40)) * Math.max(Math.abs(transform.scaleX), Math.abs(transform.scaleY));
                yield List.of(Segment.circle(cx, cy, Math.max(radius, 0)));
            }
            case "INSERT" -> expandInsert(values, blocks, transform);
            default -> List.of();
        };
    }

    private static List<Segment> buildPolyline(Map<Integer, String> values, Affine2D transform) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        values.forEach((code, val) -> {
            if (code == 10) {
                xs.add(parseDouble(val));
            } else if (code == 20) {
                ys.add(parseDouble(val));
            }
        });

        int count = Math.min(xs.size(), ys.size());
        if (count < 2) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            double x1 = transform.x(xs.get(i - 1), ys.get(i - 1));
            double y1 = transform.y(xs.get(i - 1), ys.get(i - 1));
            double x2 = transform.x(xs.get(i), ys.get(i));
            double y2 = transform.y(xs.get(i), ys.get(i));
            segments.add(Segment.line(x1, y1, x2, y2));
        }
        return segments;
    }

    private static List<Segment> expandInsert(
            Map<Integer, String> values,
            Map<String, BlockDef> blocks,
            Affine2D parentTransform) {

        String blockName = values.get(2);
        if (blockName == null) {
            return List.of();
        }
        BlockDef block = blocks.get(blockName.toUpperCase(Locale.ROOT));
        if (block == null || block.segments.isEmpty()) {
            return List.of();
        }

        Affine2D insert = parentTransform.compose(new Affine2D(
                parseDouble(values.get(10)),
                parseDouble(values.get(20)),
                parseDouble(values.get(41), 1),
                parseDouble(values.get(42), 1),
                parseDouble(values.get(50), 0)));

        List<Segment> expanded = new ArrayList<>();
        for (Segment segment : block.segments) {
            if (segment.circle) {
                double cx = insert.x(segment.x1, segment.y1);
                double cy = insert.y(segment.x1, segment.y1);
                double radius = segment.x2 * Math.max(Math.abs(insert.scaleX), Math.abs(insert.scaleY));
                expanded.add(Segment.circle(cx, cy, radius));
            } else {
                expanded.add(Segment.line(
                        insert.x(segment.x1, segment.y1),
                        insert.y(segment.x1, segment.y1),
                        insert.x(segment.x2, segment.y2),
                        insert.y(segment.x2, segment.y2)));
            }
        }
        return expanded;
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    private static final class Affine2D {
        final double tx;
        final double ty;
        final double scaleX;
        final double scaleY;
        final double rotationRad;

        Affine2D() {
            this(0, 0, 1, 1, 0);
        }

        Affine2D(double tx, double ty, double scaleX, double scaleY, double rotationDeg) {
            this.tx = tx;
            this.ty = ty;
            this.scaleX = scaleX == 0 ? 1 : scaleX;
            this.scaleY = scaleY == 0 ? 1 : scaleY;
            this.rotationRad = Math.toRadians(rotationDeg);
        }

        Affine2D compose(Affine2D other) {
            double cos = Math.cos(other.rotationRad);
            double sin = Math.sin(other.rotationRad);
            double sx = scaleX * other.scaleX;
            double sy = scaleY * other.scaleY;
            double ox = x(other.tx, other.ty);
            double oy = y(other.tx, other.ty);
            double rot = rotationRad + other.rotationRad;
            return new Affine2D(ox, oy, sx, sy, Math.toDegrees(rot));
        }

        double x(double x, double y) {
            double cos = Math.cos(rotationRad);
            double sin = Math.sin(rotationRad);
            return tx + (x * scaleX * cos) - (y * scaleY * sin);
        }

        double y(double x, double y) {
            double cos = Math.cos(rotationRad);
            double sin = Math.sin(rotationRad);
            return ty + (x * scaleX * sin) + (y * scaleY * cos);
        }
    }
}
