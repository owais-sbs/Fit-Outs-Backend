package com.fitouts.subcontractor.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.SubcontractorPackage;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SubcontractPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes) {

        return generatePdf(
                pkg,
                award,
                org,
                adminSignerName,
                adminSignerTitle,
                adminSignedAt,
                adminSignatureImageBytes,
                null,
                null,
                null,
                null,
                false);
    }

    public byte[] generateStage2FinalPdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes,
            String subSignerName,
            String subSignerTitle,
            OffsetDateTime subSignedAt,
            byte[] subSignatureImageBytes) {

        return generatePdf(
                pkg,
                award,
                org,
                adminSignerName,
                adminSignerTitle,
                adminSignedAt,
                adminSignatureImageBytes,
                subSignerName,
                subSignerTitle,
                subSignedAt,
                subSignatureImageBytes,
                true);
    }

    private byte[] generatePdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes,
            String subSignerName,
            String subSignerTitle,
            OffsetDateTime subSignedAt,
            byte[] subSignatureImageBytes,
            boolean isExecuted) {

        byte[] adminJpeg = toJpegBytes(adminSignatureImageBytes);
        byte[] subJpeg = isExecuted ? toJpegBytes(subSignatureImageBytes) : null;

        ImageDimensions adminDim = getImageDimensions(adminJpeg);
        ImageDimensions subDim = getImageDimensions(subJpeg);

        String title = isExecuted ? "SUBCONTRACT AGREEMENT (EXECUTED)" : "SUBCONTRACT AGREEMENT";
        String packageName = pkg != null && pkg.getName() != null ? pkg.getName() : "Subcontract Package";
        String orgName = org != null && org.getLegalCompanyName() != null ? org.getLegalCompanyName() : "Subcontractor";
        String value = award != null && award.getAwardedValue() != null
                ? "AED " + award.getAwardedValue().setScale(2, java.math.RoundingMode.HALF_UP).toString()
                : "AED 0.00";
        String awardDate = award != null && award.getAwardedAt() != null
                ? award.getAwardedAt().format(DATE_FORMATTER)
                : "N/A";
        String adminDateStr = adminSignedAt != null ? adminSignedAt.format(DATE_FORMATTER) : "N/A";
        String subDateStr = subSignedAt != null ? subSignedAt.format(DATE_FORMATTER) : "N/A";

        StringBuilder content = new StringBuilder();

        // PDF Content Stream Operators
        content.append("q\n");
        // Title
        content.append("0 0 0 rg\n");
        content.append("BT /F1 18 Tf 50 750 Td (").append(escapePdf(title)).append(") Tj ET\n");
        content.append("0.5 0.5 0.5 RG 50 740 m 550 740 l S\n");

        // Contract details
        content.append("0 0 0 rg\n");
        content.append("BT /F1 11 Tf 50 710 Td (Package Name: ").append(escapePdf(packageName)).append(") Tj ET\n");
        content.append("BT /F1 11 Tf 50 690 Td (Subcontractor: ").append(escapePdf(orgName)).append(") Tj ET\n");
        content.append("BT /F1 11 Tf 50 670 Td (Awarded Value: ").append(escapePdf(value)).append(") Tj ET\n");
        content.append("BT /F1 11 Tf 50 650 Td (Award Date: ").append(escapePdf(awardDate)).append(") Tj ET\n");

        // Terms section
        content.append("0.8 0.8 0.8 RG 50 630 m 550 630 l S\n");
        content.append("0 0 0 rg\n");
        content.append("BT /F1 12 Tf 50 605 Td (TERMS & AGREEMENT DECLARATION) Tj ET\n");
        content.append("BT /F1 9 Tf 50 585 Td (1. This Subcontract Agreement incorporates all tender documents, scopes, and agreed BOQ items.) Tj ET\n");
        content.append("BT /F1 9 Tf 50 570 Td (2. The Subcontractor agrees to execute the works in strict compliance with project specifications.) Tj ET\n");
        content.append("BT /F1 9 Tf 50 555 Td (3. Digital signatures attached below constitute a legally binding electronic execution.) Tj ET\n");

        content.append("0.8 0.8 0.8 RG 50 535 m 550 535 l S\n");

        // Signature Boxes Header
        content.append("0 0 0 rg\n");
        content.append("BT /F1 12 Tf 50 510 Td (EXECUTION & SIGNATURES) Tj ET\n");

        // Box 1: Admin (Left)
        content.append("0.95 0.95 0.95 rg 50 360 230 130 re f 0.7 0.7 0.7 RG 50 360 230 130 re S\n");
        content.append("0 0 0 rg\n");
        content.append("BT /F1 10 Tf 60 475 Td (ADMIN) Tj ET\n");
        content.append("BT /F1 9 Tf 60 460 Td (Name: ").append(escapePdf(adminSignerName != null ? adminSignerName : "Admin")).append(") Tj ET\n");
        if (adminSignerTitle != null && !adminSignerTitle.isBlank()) {
            content.append("BT /F1 9 Tf 60 448 Td (Title: ").append(escapePdf(adminSignerTitle)).append(") Tj ET\n");
        }
        content.append("BT /F1 8 Tf 60 435 Td (Signed: ").append(escapePdf(adminDateStr)).append(") Tj ET\n");
        if (adminJpeg != null) {
            content.append("q 120 0 0 45 60 380 cm /Im1 Do Q\n");
        }

        // Box 2: Subcontractor (Right)
        content.append("0.95 0.95 0.95 rg 310 360 230 130 re f 0.7 0.7 0.7 RG 310 360 230 130 re S\n");
        content.append("0 0 0 rg\n");
        content.append("BT /F1 10 Tf 320 475 Td (SUBCONTRACTOR) Tj ET\n");
        if (isExecuted) {
            content.append("BT /F1 9 Tf 320 460 Td (Name: ").append(escapePdf(subSignerName != null ? subSignerName : orgName)).append(") Tj ET\n");
            if (subSignerTitle != null && !subSignerTitle.isBlank()) {
                content.append("BT /F1 9 Tf 320 448 Td (Title: ").append(escapePdf(subSignerTitle)).append(") Tj ET\n");
            }
            content.append("BT /F1 8 Tf 320 435 Td (Signed: ").append(escapePdf(subDateStr)).append(") Tj ET\n");
            if (subJpeg != null) {
                content.append("q 120 0 0 45 320 380 cm /Im2 Do Q\n");
            }
        } else {
            content.append("0.4 0.4 0.4 rg BT /F1 10 Tf 320 430 Td ([ WAITING FOR SUBCONTRACTOR SIGNATURE ]) Tj ET\n");
        }

        content.append("Q\n");

        return assemblePdf(content.toString(), adminJpeg, adminDim, subJpeg, subDim);
    }

    private byte[] assemblePdf(
            String contentStream,
            byte[] adminJpeg,
            ImageDimensions adminDim,
            byte[] subJpeg,
            ImageDimensions subDim) {

        try {
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            List<Long> xrefPositions = new ArrayList<>();

            writeString(pdfOut, "%PDF-1.4\n");

            // Dynamic PDF object ID assignments
            int nextObj = 1;
            int catalogObjId = nextObj++;   // 1
            int pagesObjId = nextObj++;     // 2
            int pageObjId = nextObj++;      // 3
            int fontObjId = nextObj++;      // 4

            Integer adminImgObjId = null;
            if (adminJpeg != null) {
                adminImgObjId = nextObj++;
            }

            Integer subImgObjId = null;
            if (subJpeg != null) {
                subImgObjId = nextObj++;
            }

            int contentObjId = nextObj++;

            // Object 1: Catalog
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, catalogObjId + " 0 obj\n<< /Type /Catalog /Pages " + pagesObjId + " 0 R >>\nendobj\n");

            // Object 2: Pages
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pagesObjId + " 0 obj\n<< /Type /Pages /Count 1 /Kids [" + pageObjId + " 0 R] >>\nendobj\n");

            // Resource dictionary
            StringBuilder resDict = new StringBuilder();
            resDict.append("<< /Font << /F1 ").append(fontObjId).append(" 0 R >> /XObject << ");
            if (adminImgObjId != null) {
                resDict.append("/Im1 ").append(adminImgObjId).append(" 0 R ");
            }
            if (subImgObjId != null) {
                resDict.append("/Im2 ").append(subImgObjId).append(" 0 R ");
            }
            resDict.append(">> >>");

            // Object 3: Page (pointing to actual contentObjId)
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pageObjId + " 0 obj\n<< /Type /Page /Parent " + pagesObjId + " 0 R /MediaBox [0 0 595 842] /Resources "
                    + resDict.toString() + " /Contents " + contentObjId + " 0 R >>\nendobj\n");

            // Object 4: Font F1
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontObjId + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            // Object 5 & 6: Images (if present)
            if (adminImgObjId != null) {
                xrefPositions.add((long) pdfOut.size());
                writeImageObj(pdfOut, adminImgObjId, adminJpeg, adminDim);
            }
            if (subImgObjId != null) {
                xrefPositions.add((long) pdfOut.size());
                writeImageObj(pdfOut, subImgObjId, subJpeg, subDim);
            }

            // Object contentObjId: Content Stream
            xrefPositions.add((long) pdfOut.size());
            byte[] contentBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);
            writeString(pdfOut, contentObjId + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            pdfOut.write(contentBytes);
            writeString(pdfOut, "\nendstream\nendobj\n");

            // Cross-Reference Table (XRef)
            long startXref = pdfOut.size();
            writeString(pdfOut, "xref\n0 " + (xrefPositions.size() + 1) + "\n");
            writeString(pdfOut, "0000000000 65535 f \n");
            for (Long pos : xrefPositions) {
                writeString(pdfOut, String.format("%010d 00000 n \n", pos));
            }

            // Trailer
            writeString(pdfOut, "trailer\n<< /Size " + (xrefPositions.size() + 1) + " /Root " + catalogObjId + " 0 R >>\n");
            writeString(pdfOut, "startxref\n" + startXref + "\n%%EOF\n");

            return pdfOut.toByteArray();
        } catch (IOException e) {
            log.error("Failed to assemble PDF", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void writeImageObj(ByteArrayOutputStream out, int objId, byte[] jpegBytes, ImageDimensions dim) throws IOException {
        int w = dim != null && dim.width > 0 ? dim.width : 200;
        int h = dim != null && dim.height > 0 ? dim.height : 80;
        writeString(out, objId + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpegBytes.length + " >>\nstream\n");
        out.write(jpegBytes);
        writeString(out, "\nendstream\nendobj\n");
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private record ImageDimensions(int width, int height) {}

    private ImageDimensions getImageDimensions(byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length == 0) return new ImageDimensions(200, 80);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(jpegBytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img != null) {
                return new ImageDimensions(img.getWidth(), img.getHeight());
            }
        } catch (Exception ignored) {
        }
        return new ImageDimensions(200, 80);
    }

    private byte[] toJpegBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) return null;
            BufferedImage rgbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImg.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
            g2d.drawImage(img, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgbImg, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to convert signature image to JPEG: {}", e.getMessage());
            return null;
        }
    }

    private String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
