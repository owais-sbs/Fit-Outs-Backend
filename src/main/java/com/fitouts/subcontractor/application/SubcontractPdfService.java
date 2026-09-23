package com.fitouts.subcontractor.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.project.domain.Project;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.SubcontractorPackage;

import lombok.extern.slf4j.Slf4j;

/**
 * Generates the official JCT-style subcontract agreement PDF
 * (same visual language as the BOQ / site-visit Cover Letter).
 */
@Service
@Slf4j
public class SubcontractPdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

    private static final String[] JCT_SUMMARY = {
            "Project Mobilization: 15 working days from Advance Payment, NOC, Permit Access, Approved Shop Drawings and Materials Approval (whichever is later).",
            "Project Duration: Estimated 75 working days after mobilization (or as stated in the package programme).",
            "Payment Terms: As stated in this agreement / package; all JCT invoices settled within 7 calendar days.",
            "VAT: Awarded BOQ prices exclude VAT. JCT charges 5% VAT per UAE / Dubai regulations.",
            "Working Days: 8 hours/day (8 am to 5 pm), 5 days/week unless otherwise restricted by NOC or Authorities.",
            "Defect Liability Period (DLP): 12 months from when the Client moves in.",
            "BOQ quantities are estimates; final payment is based on actual site measurements.",
            "Work not stated in the Bill of Quantities is additional work requiring written approval.",
            "Acceptance of this subcontract award implies acceptance of JCT Terms and Conditions."
    };

    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            Project project,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes) {

        return generatePdf(
                pkg, award, org, project,
                adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes,
                null, null, null, null, false);
    }

    public byte[] generateStage2FinalPdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            Project project,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes,
            String subSignerName,
            String subSignerTitle,
            OffsetDateTime subSignedAt,
            byte[] subSignatureImageBytes) {

        return generatePdf(
                pkg, award, org, project,
                adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes,
                subSignerName, subSignerTitle, subSignedAt, subSignatureImageBytes, true);
    }

    /** Backward-compatible overload (no project). */
    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes) {
        return generateStage1AdminPdf(
                pkg, award, org, null, adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes);
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
        return generateStage2FinalPdf(
                pkg, award, org, null,
                adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes,
                subSignerName, subSignerTitle, subSignedAt, subSignatureImageBytes);
    }

    private byte[] generatePdf(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            Project project,
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

        List<String> pages = new ArrayList<>();
        pages.add(buildCoverPage(pkg, award, org, project, isExecuted));
        pages.add(buildTermsPage(pkg, award));
        pages.add(buildSignaturePage(
                pkg, award, org,
                adminSignerName, adminSignerTitle, adminSignedAt, adminJpeg != null,
                subSignerName, subSignerTitle, subSignedAt, subJpeg != null, isExecuted));

        return assembleMultiPagePdf(pages, adminJpeg, adminDim, subJpeg, subDim);
    }

    private String buildCoverPage(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            Project project,
            boolean isExecuted) {

        String packageName = text(pkg != null ? pkg.getName() : null, "Subcontract Package");
        String orgName = text(org != null ? org.getLegalCompanyName() : null,
                pkg != null ? pkg.getAppointedCompanyName() : null, "Subcontractor");
        String projectName = text(project != null ? project.getName() : null, "Project");
        String location = text(project != null ? project.getLocation() : null, "Dubai, United Arab Emirates");
        String value = formatMoney(award != null ? award.getAwardedValue() : null);
        String awardDate = formatDate(award != null && award.getAwardedAt() != null
                ? award.getAwardedAt().toLocalDate() : LocalDate.now());
        String trade = tradeLabel(pkg);
        String ref = "SC-" + (pkg != null && pkg.getUuid() != null
                ? pkg.getUuid().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : "DRAFT");

        StringBuilder c = new StringBuilder();
        c.append("q\n");

        // Dark JCT header bar
        c.append("0.122 0.227 0.204 rg 0 762 595 80 re f\n");
        c.append("1 1 1 rg\n");
        c.append("BT /F1 9 Tf 40 820 Td (SUBCONTRACT AWARD) Tj ET\n");
        c.append("BT /F2 20 Tf 40 795 Td (JCT Contracting) Tj ET\n");
        c.append("BT /F1 9 Tf 40 778 Td (Premium Fit-Out & Interior Solutions) Tj ET\n");
        c.append("0.784 0.663 0.494 rg\n");
        c.append("BT /F1 10 Tf 400 815 Td (Cover Letter) Tj ET\n");
        c.append("1 1 1 rg\n");
        c.append("BT /F1 10 Tf 400 795 Td (").append(escapePdf(ref)).append(") Tj ET\n");
        c.append("BT /F1 8 Tf 400 780 Td (").append(escapePdf(isExecuted ? "EXECUTED" : "PENDING EXECUTION")).append(") Tj ET\n");

        // Meta strip
        c.append("0.969 0.961 0.949 rg 0 700 595 62 re f\n");
        c.append("0.898 0.882 0.855 RG 0 700 m 595 700 l S\n");
        c.append("0.42 0.42 0.42 rg\n");
        c.append("BT /F1 8 Tf 40 745 Td (Date) Tj ET\n");
        c.append("0 0 0 rg BT /F1 10 Tf 40 730 Td (").append(escapePdf(awardDate)).append(") Tj ET\n");
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 40 715 Td (Awarded value \\(excl. VAT\\)) Tj ET\n");
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 320 745 Td (Project) Tj ET\n");
        c.append("0 0 0 rg BT /F1 10 Tf 320 730 Td (").append(escapePdf(clip(projectName, 42))).append(") Tj ET\n");
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 320 715 Td (Location) Tj ET\n");
        c.append("0 0 0 rg BT /F1 9 Tf 40 702 Td (AED ").append(escapePdf(value)).append(") Tj ET\n");
        c.append("BT /F1 9 Tf 320 702 Td (").append(escapePdf(clip(location, 42))).append(") Tj ET\n");

        float y = 670;
        y = drawLabelValue(c, 40, y, "TO", orgName);
        y -= 8;
        y = drawLabelValue(c, 40, y, "PACKAGE", packageName);
        if (StringUtils.hasText(trade)) {
            y -= 4;
            y = drawLabelValue(c, 40, y, "TRADE", trade);
        }
        y -= 14;
        y = drawLabelValue(c, 40, y, "SUBJECT",
                "SUBCONTRACT AWARD — " + clip(packageName, 48).toUpperCase(Locale.ROOT));

        y -= 18;
        String[] body = {
                "We are pleased to confirm the award of the above subcontract package for your execution under the Main Contract works.",
                "This Subcontract Agreement incorporates the tender documents, agreed priced BOQ, package programme, free-issue / attendance schedules, and the JCT Terms and Conditions attached hereto.",
                "The awarded prices are exclusive of Value Added Tax. Please refer to the agreed BOQ for the priced breakdown.",
                "The Subcontractor shall execute the works in strict compliance with the project specifications, drawings, and HSE / site rules.",
                "Digital signatures on the execution page constitute a legally binding electronic execution of this agreement.",
                "Looking forward to a successful mobilisation and delivery."
        };
        c.append("0.12 0.16 0.22 rg\n");
        for (String para : body) {
            y = drawWrapped(c, para, 40, y, 515, 10, 13);
            y -= 8;
        }

        // Total box
        y -= 6;
        c.append("0.969 0.961 0.949 rg 40 ").append(y - 52).append(" 515 56 re f\n");
        c.append("0.122 0.227 0.204 RG 40 ").append(y - 52).append(" 515 56 re S\n");
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 52 ").append(y - 12).append(" Td (AWARDED SUBCONTRACT VALUE) Tj ET\n");
        c.append("0.122 0.227 0.204 rg BT /F2 18 Tf 52 ").append(y - 36).append(" Td (AED ")
                .append(escapePdf(value)).append(") Tj ET\n");
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 52 ").append(y - 48).append(" Td (Exclusive of VAT · from agreed BOQ / award) Tj ET\n");

        y -= 78;
        c.append("0 0 0 rg\n");
        c.append("BT /F1 10 Tf 40 ").append(y).append(" Td (Respectfully,) Tj ET\n");
        y -= 16;
        c.append("BT /F1 10 Tf 40 ").append(y).append(" Td (For) Tj ET\n");
        y -= 14;
        c.append("BT /F2 11 Tf 40 ").append(y).append(" Td (JCT Contracting) Tj ET\n");
        y -= 28;
        c.append("BT /F1 10 Tf 40 ").append(y).append(" Td (Grigoris Georgiou) Tj ET\n");
        y -= 12;
        c.append("0.42 0.42 0.42 rg BT /F1 9 Tf 40 ").append(y).append(" Td (Projects Director) Tj ET\n");
        y -= 24;
        c.append("0.122 0.227 0.204 rg BT /F1 9 Tf 40 ").append(y)
                .append(" Td (Thank you for your business!) Tj ET\n");

        // Footer
        c.append("0.7 0.7 0.7 RG 40 40 m 555 40 l S\n");
        c.append("0.45 0.45 0.45 rg BT /F1 7 Tf 40 28 Td (JCT Contracting  |  Subcontract Agreement  |  Page 1 of 3) Tj ET\n");
        c.append("Q\n");
        return c.toString();
    }

    private String buildTermsPage(SubcontractorPackage pkg, ScPackageAward award) {
        StringBuilder c = new StringBuilder();
        c.append("q\n");
        c.append("0.122 0.227 0.204 rg 0 802 595 40 re f\n");
        c.append("1 1 1 rg BT /F2 14 Tf 40 816 Td (Commercial Terms & JCT Conditions) Tj ET\n");

        float y = 770;
        c.append("0 0 0 rg\n");
        y = drawSectionTitle(c, 40, y, "PACKAGE COMMERCIAL TERMS");
        y -= 6;

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Payment terms", text(pkg != null ? pkg.getPaymentTerms() : null,
                "Per JCT standard payment schedule (see summary below)")});
        rows.add(new String[]{"Retention", pkg != null && pkg.getRetentionPct() != null
                ? pkg.getRetentionPct().stripTrailingZeros().toPlainString() + "%"
                : "As per Main Contract / package (DLP 12 months)"});
        rows.add(new String[]{"LD / delay recovery", text(pkg != null ? pkg.getLdTerms() : null,
                "Per JCT delay and non-payment recovery clauses")});
        if (pkg != null && (pkg.getPlannedStart() != null || pkg.getPlannedFinish() != null)) {
            rows.add(new String[]{"Programme",
                    (pkg.getPlannedStart() != null ? "Start " + pkg.getPlannedStart() : "")
                            + (pkg.getPlannedStart() != null && pkg.getPlannedFinish() != null ? " · " : "")
                            + (pkg.getPlannedFinish() != null ? "Finish " + pkg.getPlannedFinish() : "")});
        }
        if (pkg != null && StringUtils.hasText(pkg.getActivityCodes())) {
            rows.add(new String[]{"Activity codes", pkg.getActivityCodes().trim()});
        }
        if (pkg != null && StringUtils.hasText(pkg.getTenderDescription())) {
            rows.add(new String[]{"Scope inclusions", clip(pkg.getTenderDescription().trim(), 280)});
        }
        if (award != null && award.getAwardedValue() != null) {
            rows.add(new String[]{"Awarded value", "AED " + formatMoney(award.getAwardedValue()) + " (excl. VAT)"});
        }

        for (String[] row : rows) {
            c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 40 ").append(y).append(" Td (")
                    .append(escapePdf(row[0].toUpperCase(Locale.ROOT))).append(") Tj ET\n");
            y -= 12;
            y = drawWrapped(c, row[1], 40, y, 515, 9, 12);
            y -= 10;
            if (y < 120) break;
        }

        y -= 6;
        y = drawSectionTitle(c, 40, y, "JCT TERMS & CONDITIONS — SUMMARY");
        y -= 4;
        int i = 1;
        for (String clause : JCT_SUMMARY) {
            if (y < 70) break;
            y = drawWrapped(c, i + ". " + clause, 40, y, 515, 8, 11);
            y -= 6;
            i++;
        }

        c.append("0.7 0.7 0.7 RG 40 40 m 555 40 l S\n");
        c.append("0.45 0.45 0.45 rg BT /F1 7 Tf 40 28 Td (Full JCT Terms & Conditions form part of this agreement  |  Page 2 of 3) Tj ET\n");
        c.append("Q\n");
        return c.toString();
    }

    private String buildSignaturePage(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScOrganization org,
            String adminSignerName,
            String adminSignerTitle,
            OffsetDateTime adminSignedAt,
            boolean hasAdminSig,
            String subSignerName,
            String subSignerTitle,
            OffsetDateTime subSignedAt,
            boolean hasSubSig,
            boolean isExecuted) {

        String packageName = text(pkg != null ? pkg.getName() : null, "Package");
        String orgName = text(org != null ? org.getLegalCompanyName() : null, "Subcontractor");
        String adminDate = adminSignedAt != null ? adminSignedAt.format(DATE_TIME) : "—";
        String subDate = subSignedAt != null ? subSignedAt.format(DATE_TIME) : "—";

        StringBuilder c = new StringBuilder();
        c.append("q\n");
        c.append("0.122 0.227 0.204 rg 0 802 595 40 re f\n");
        c.append("1 1 1 rg BT /F2 14 Tf 40 816 Td (Execution & Signatures) Tj ET\n");

        float y = 770;
        c.append("0 0 0 rg\n");
        y = drawWrapped(c,
                "By signing below, the parties confirm award of \"" + packageName
                        + "\" to " + orgName + " for AED "
                        + formatMoney(award != null ? award.getAwardedValue() : null)
                        + " (excl. VAT), and accept the commercial terms and JCT Terms & Conditions forming part of this Subcontract Agreement.",
                40, y, 515, 9, 12);
        y -= 24;

        // Admin box
        c.append("0.969 0.961 0.949 rg 40 ").append(y - 160).append(" 240 160 re f\n");
        c.append("0.7 0.7 0.7 RG 40 ").append(y - 160).append(" 240 160 re S\n");
        c.append("0.122 0.227 0.204 rg BT /F2 11 Tf 52 ").append(y - 16).append(" Td (MAIN CONTRACTOR — JCT) Tj ET\n");
        c.append("0 0 0 rg\n");
        c.append("BT /F1 9 Tf 52 ").append(y - 34).append(" Td (Name: ")
                .append(escapePdf(text(adminSignerName, "Admin"))).append(") Tj ET\n");
        if (StringUtils.hasText(adminSignerTitle)) {
            c.append("BT /F1 9 Tf 52 ").append(y - 48).append(" Td (Title: ")
                    .append(escapePdf(adminSignerTitle)).append(") Tj ET\n");
        }
        c.append("BT /F1 8 Tf 52 ").append(y - 62).append(" Td (Signed: ")
                .append(escapePdf(adminDate)).append(") Tj ET\n");
        if (hasAdminSig) {
            c.append("q 130 0 0 48 52 ").append(y - 145).append(" cm /Im1 Do Q\n");
        }

        // Subcontractor box
        c.append("0.969 0.961 0.949 rg 315 ").append(y - 160).append(" 240 160 re f\n");
        c.append("0.7 0.7 0.7 RG 315 ").append(y - 160).append(" 240 160 re S\n");
        c.append("0.122 0.227 0.204 rg BT /F2 11 Tf 327 ").append(y - 16).append(" Td (SUBCONTRACTOR) Tj ET\n");
        c.append("0 0 0 rg\n");
        if (isExecuted) {
            c.append("BT /F1 9 Tf 327 ").append(y - 34).append(" Td (Name: ")
                    .append(escapePdf(text(subSignerName, orgName))).append(") Tj ET\n");
            if (StringUtils.hasText(subSignerTitle)) {
                c.append("BT /F1 9 Tf 327 ").append(y - 48).append(" Td (Title: ")
                        .append(escapePdf(subSignerTitle)).append(") Tj ET\n");
            }
            c.append("BT /F1 8 Tf 327 ").append(y - 62).append(" Td (Signed: ")
                    .append(escapePdf(subDate)).append(") Tj ET\n");
            if (hasSubSig) {
                c.append("q 130 0 0 48 327 ").append(y - 145).append(" cm /Im2 Do Q\n");
            }
        } else {
            c.append("0.45 0.45 0.45 rg BT /F1 9 Tf 327 ").append(y - 80)
                    .append(" Td ([ Waiting for Subcontractor signature ]) Tj ET\n");
        }

        y -= 190;
        c.append("0.42 0.42 0.42 rg\n");
        y = drawWrapped(c,
                "This electronically signed document is the official subcontract award record. "
                        + "It should be read together with the agreed priced BOQ and package schedules shared in the portal.",
                40, y, 515, 8, 11);

        c.append("0.7 0.7 0.7 RG 40 40 m 555 40 l S\n");
        c.append("0.45 0.45 0.45 rg BT /F1 7 Tf 40 28 Td (JCT Contracting  |  Subcontract Agreement  |  Page 3 of 3) Tj ET\n");
        c.append("Q\n");
        return c.toString();
    }

    private float drawSectionTitle(StringBuilder c, float x, float y, String title) {
        c.append("0.122 0.227 0.204 rg BT /F2 11 Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(escapePdf(title)).append(") Tj ET\n");
        c.append("0.784 0.663 0.494 RG ").append(x).append(' ').append(y - 4).append(" m ")
                .append(x + 200).append(' ').append(y - 4).append(" l S\n");
        return y - 18;
    }

    private float drawLabelValue(StringBuilder c, float x, float y, String label, String value) {
        c.append("0.42 0.42 0.42 rg BT /F1 8 Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(escapePdf(label)).append(") Tj ET\n");
        c.append("0 0 0 rg BT /F2 11 Tf ").append(x).append(' ').append(y - 14)
                .append(" Td (").append(escapePdf(clip(value, 70))).append(") Tj ET\n");
        return y - 28;
    }

    private float drawWrapped(StringBuilder c, String text, float x, float startY, float maxWidth,
                              float fontSize, float leading) {
        List<String> lines = wrapText(text, (int) (maxWidth / (fontSize * 0.5f)));
        float y = startY;
        c.append("0.12 0.16 0.22 rg\n");
        for (String line : lines) {
            c.append("BT /F1 ").append(fontSize).append(" Tf ").append(x).append(' ').append(y)
                    .append(" Td (").append(escapePdf(line)).append(") Tj ET\n");
            y -= leading;
        }
        return y;
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            lines.add("");
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private byte[] assembleMultiPagePdf(
            List<String> pageContents,
            byte[] adminJpeg,
            ImageDimensions adminDim,
            byte[] subJpeg,
            ImageDimensions subDim) {

        try {
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            List<Long> xrefPositions = new ArrayList<>();

            writeString(pdfOut, "%PDF-1.4\n");

            int nextObj = 1;
            int catalogObjId = nextObj++;
            int pagesObjId = nextObj++;
            int fontRegularId = nextObj++;
            int fontBoldId = nextObj++;

            Integer adminImgObjId = null;
            if (adminJpeg != null) adminImgObjId = nextObj++;
            Integer subImgObjId = null;
            if (subJpeg != null) subImgObjId = nextObj++;

            int pageCount = pageContents.size();
            int[] pageObjIds = new int[pageCount];
            int[] contentObjIds = new int[pageCount];
            for (int i = 0; i < pageCount; i++) {
                pageObjIds[i] = nextObj++;
                contentObjIds[i] = nextObj++;
            }

            // Catalog
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, catalogObjId + " 0 obj\n<< /Type /Catalog /Pages " + pagesObjId + " 0 R >>\nendobj\n");

            // Pages
            StringBuilder kids = new StringBuilder("[");
            for (int id : pageObjIds) kids.append(id).append(" 0 R ");
            kids.append("]");
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pagesObjId + " 0 obj\n<< /Type /Pages /Count " + pageCount
                    + " /Kids " + kids + " >>\nendobj\n");

            // Fonts
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontRegularId + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontBoldId + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            if (adminImgObjId != null) {
                xrefPositions.add((long) pdfOut.size());
                writeImageObj(pdfOut, adminImgObjId, adminJpeg, adminDim);
            }
            if (subImgObjId != null) {
                xrefPositions.add((long) pdfOut.size());
                writeImageObj(pdfOut, subImgObjId, subJpeg, subDim);
            }

            StringBuilder resDict = new StringBuilder();
            resDict.append("<< /Font << /F1 ").append(fontRegularId).append(" 0 R /F2 ")
                    .append(fontBoldId).append(" 0 R >> /XObject << ");
            if (adminImgObjId != null) resDict.append("/Im1 ").append(adminImgObjId).append(" 0 R ");
            if (subImgObjId != null) resDict.append("/Im2 ").append(subImgObjId).append(" 0 R ");
            resDict.append(">> >>");

            for (int i = 0; i < pageCount; i++) {
                xrefPositions.add((long) pdfOut.size());
                writeString(pdfOut, pageObjIds[i] + " 0 obj\n<< /Type /Page /Parent " + pagesObjId
                        + " 0 R /MediaBox [0 0 595 842] /Resources " + resDict
                        + " /Contents " + contentObjIds[i] + " 0 R >>\nendobj\n");

                byte[] contentBytes = pageContents.get(i).getBytes(StandardCharsets.ISO_8859_1);
                xrefPositions.add((long) pdfOut.size());
                writeString(pdfOut, contentObjIds[i] + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
                pdfOut.write(contentBytes);
                writeString(pdfOut, "\nendstream\nendobj\n");
            }

            long startXref = pdfOut.size();
            writeString(pdfOut, "xref\n0 " + (xrefPositions.size() + 1) + "\n");
            writeString(pdfOut, "0000000000 65535 f \n");
            for (Long pos : xrefPositions) {
                writeString(pdfOut, String.format("%010d 00000 n \n", pos));
            }
            writeString(pdfOut, "trailer\n<< /Size " + (xrefPositions.size() + 1)
                    + " /Root " + catalogObjId + " 0 R >>\n");
            writeString(pdfOut, "startxref\n" + startXref + "\n%%EOF\n");
            return pdfOut.toByteArray();
        } catch (IOException e) {
            log.error("Failed to assemble PDF", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void writeImageObj(ByteArrayOutputStream out, int objId, byte[] jpegBytes, ImageDimensions dim)
            throws IOException {
        int w = dim != null && dim.width > 0 ? dim.width : 200;
        int h = dim != null && dim.height > 0 ? dim.height : 80;
        writeString(out, objId + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpegBytes.length + " >>\nstream\n");
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
            if (img != null) return new ImageDimensions(img.getWidth(), img.getHeight());
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

    private static String formatMoney(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(DATE_LONG);
    }

    private static String tradeLabel(SubcontractorPackage pkg) {
        if (pkg == null) return null;
        if (StringUtils.hasText(pkg.getTradePackageName()) && StringUtils.hasText(pkg.getTradePackageCode())) {
            return pkg.getTradePackageCode() + " — " + pkg.getTradePackageName();
        }
        if (StringUtils.hasText(pkg.getTradePackageName())) return pkg.getTradePackageName();
        return pkg.getTradePackageCode();
    }

    private static String text(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return "";
    }

    private static String clip(String text, int max) {
        if (!StringUtils.hasText(text)) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
