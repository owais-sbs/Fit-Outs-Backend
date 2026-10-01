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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.project.domain.Project;
import com.fitouts.subcontractor.api.ScAwardBoqLineResponse;
import com.fitouts.subcontractor.domain.ScFreeIssueMaterial;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScPackageAttendance;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.SubcontractorPackage;

import lombok.extern.slf4j.Slf4j;

/**
 * Server PDF that mirrors {@code SubcontractAgreementLetter.jsx} (SC portal preview).
 * Balanced A4 print density — readable typography with a logical page break before T&amp;C.
 */
@Service
@Slf4j
public class SubcontractPdfService {

    private static final DateTimeFormatter DATE_META = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    /** A4; ~15 mm margins. No aggressive scale — full 100% layout. */
    private static final float PAGE_W = 595f;
    private static final float PAGE_H = 842f;
    private static final float ML = 42f;
    private static final float MT = 36f;
    private static final float MB = 46f;
    private static final float CW = PAGE_W - ML - ML;
    private static final float FOOTER_Y = 28f;

    /** Typography (pt) — print-readable, not compressed. */
    private static final float FONT_TITLE = 17f;
    private static final float FONT_SECTION = 10.5f;
    private static final float FONT_BODY = 9.5f;
    private static final float FONT_TABLE = 8.5f;
    private static final float FONT_META = 9f;
    private static final float FONT_FOOTER = 7.5f;
    private static final float LEAD_BODY = 12.5f;  // ~1.32 × 9.5
    private static final float LEAD_TABLE = 10.5f; // ~1.24 × 8.5
    private static final float GAP_MAJOR = 10f;
    private static final float GAP_SUB = 7f;
    private static final float GAP_PARA = 3f;

    private static final String[] AWARD_BODY = {
            "We are pleased to confirm the award of the above subcontract package for your execution under the Main Contract works.",
            "This Subcontract Agreement incorporates the tender documents, agreed priced BOQ, package programme, free-issue / attendance schedules, and the JCT Terms and Conditions below.",
            "The awarded prices are exclusive of Value Added Tax. Please refer to the agreed BOQ for the priced breakdown.",
            "The Subcontractor shall execute the works in strict compliance with the project specifications, drawings, and HSE / site rules.",
            "Digital signatures on the execution section constitute a legally binding electronic execution of this agreement."
    };

    private static final String[][] TERMS_SUMMARY = {
            {"Project Mobilization", "15 working days from receipt of the Advance Payment, the NOC, Permit Access Permit, Approved Shop Drawings and Materials Approval (whichever is later)."},
            {"Project Duration", "Estimated 75 working days after mobilization."},
            {"Approvals", "100% advance for processing the drawings for Authorities approval."},
            {"Payment Terms", "50% advance with order; 45% progress; 5% (max AED 10,000) before Client move-in. Purchases 100% advance. JCT invoices settled within 7 calendar days."},
            {"Working Days", "8 hours/day (8 am to 5 pm), 5 days/week. NOC / Authority restrictions may affect Project Duration."},
            {"Value-Added-Tax", "Quoted BOQ prices exclude VAT. JCT charges 5% VAT per UAE / Dubai regulations."},
    };

    private static final String[] TERMS_CLAUSES = {
            "Authority actual fees related to application review, modifications and/or extensions are excluded. The Client is also responsible for processing the Refundable Deposit.",
            "Should the Client decide to proceed with another contractor after Authorities' approval, JCT is entitled to an additional AED 10,000 compensation fee. The Client must also remove JCT as the main contractor.",
            "Work not stated in the Bill of Quantities (BOQ) will be treated as additional work requiring Client approval and may incur extra fees and/or duration.",
            "Delays in settling invoices automatically impact the Project Duration and completion certificate application. JCT reserves the right to stop work after 10 days of non-payment.",
            "If the Client delays the project beyond 30 days, JCT reserves the right to review costing and charge additional mobilization costs.",
            "A 10% compensation fee applies if the Client removes contracted works to perform them directly or through others.",
            "Additional administrative fees apply if the Client uses their own suppliers requiring JCT support.",
            "JCT is not responsible for delays caused by third-party suppliers or designers hired by the Client."
    };

    // ── Public API ──────────────────────────────────────────────────────────

    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, Project project,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes, List<ScAwardBoqLineResponse> awardedBoqLines) {
        return generateStage1AdminPdf(pkg, award, org, project, adminSignerName, adminSignerTitle,
                adminSignedAt, adminSignatureImageBytes, awardedBoqLines, List.of(), List.of(), null);
    }

    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, Project project,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes, List<ScAwardBoqLineResponse> awardedBoqLines,
            List<ScFreeIssueMaterial> freeIssue, List<ScPackageAttendance> attendance,
            String exclusionsText) {
        // freeIssue/attendance intentionally not rendered as separate sections —
        // portal preview folds those into commercial terms / letter body only.
        return generate(new Ctx(pkg, award, org, project,
                awardedBoqLines != null ? awardedBoqLines : List.of(), exclusionsText,
                adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes,
                null, null, null, null, false));
    }

    public byte[] generateStage2FinalPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, Project project,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes, String subSignerName, String subSignerTitle,
            OffsetDateTime subSignedAt, byte[] subSignatureImageBytes,
            List<ScAwardBoqLineResponse> awardedBoqLines) {
        return generateStage2FinalPdf(pkg, award, org, project, adminSignerName, adminSignerTitle,
                adminSignedAt, adminSignatureImageBytes, subSignerName, subSignerTitle, subSignedAt,
                subSignatureImageBytes, awardedBoqLines, List.of(), List.of(), null);
    }

    public byte[] generateStage2FinalPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, Project project,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes, String subSignerName, String subSignerTitle,
            OffsetDateTime subSignedAt, byte[] subSignatureImageBytes,
            List<ScAwardBoqLineResponse> awardedBoqLines,
            List<ScFreeIssueMaterial> freeIssue, List<ScPackageAttendance> attendance,
            String exclusionsText) {
        return generate(new Ctx(pkg, award, org, project,
                awardedBoqLines != null ? awardedBoqLines : List.of(), exclusionsText,
                adminSignerName, adminSignerTitle, adminSignedAt, adminSignatureImageBytes,
                subSignerName, subSignerTitle, subSignedAt, subSignatureImageBytes, true));
    }

    public byte[] generateStage1AdminPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes) {
        return generateStage1AdminPdf(pkg, award, org, null, adminSignerName, adminSignerTitle,
                adminSignedAt, adminSignatureImageBytes, List.of(), List.of(), List.of(), null);
    }

    public byte[] generateStage2FinalPdf(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt,
            byte[] adminSignatureImageBytes, String subSignerName, String subSignerTitle,
            OffsetDateTime subSignedAt, byte[] subSignatureImageBytes) {
        return generateStage2FinalPdf(pkg, award, org, null, adminSignerName, adminSignerTitle,
                adminSignedAt, adminSignatureImageBytes, subSignerName, subSignerTitle,
                subSignedAt, subSignatureImageBytes, List.of(), List.of(), List.of(), null);
    }

    // ── Context ─────────────────────────────────────────────────────────────

    private record Ctx(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, Project project,
            List<ScAwardBoqLineResponse> boqLines, String exclusionsText,
            String adminSignerName, String adminSignerTitle, OffsetDateTime adminSignedAt, byte[] adminSigBytes,
            String subSignerName, String subSignerTitle, OffsetDateTime subSignedAt, byte[] subSigBytes,
            boolean executed) {

        String packageName() { return nz(pkg != null ? pkg.getName() : null, "Subcontract Package"); }
        String orgName() {
            return nz(org != null ? org.getLegalCompanyName() : null,
                    pkg != null ? pkg.getAppointedCompanyName() : null, "Subcontractor");
        }
        String projectName() { return nz(project != null ? project.getName() : null, "-"); }
        String location() {
            return nz(project != null ? project.getLocation() : null, "Dubai, United Arab Emirates");
        }
        String ref() {
            if (pkg != null && pkg.getUuid() != null) {
                return "SC-" + pkg.getUuid().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            }
            return "SC-DRAFT";
        }
        String tradeLabel() {
            if (pkg == null) return null;
            if (StringUtils.hasText(pkg.getTradePackageName()) && StringUtils.hasText(pkg.getTradePackageCode())) {
                return pkg.getTradePackageCode() + " - " + pkg.getTradePackageName();
            }
            return nz(pkg.getTradePackageName(), pkg.getTradePackageCode());
        }
        String statusLabel() {
            if (executed) return "EXECUTED";
            if (adminSignedAt != null) return "PENDING SUBCONTRACTOR";
            return "PENDING ADMIN";
        }
        BigDecimal awardValue() { return award != null ? award.getAwardedValue() : null; }
        BigDecimal boqSubtotal() {
            BigDecimal sum = BigDecimal.ZERO;
            for (ScAwardBoqLineResponse line : boqLines) {
                if (isIncluded(line) && line.getAmount() != null) sum = sum.add(line.getAmount());
            }
            return sum;
        }
    }

    private static boolean isIncluded(ScAwardBoqLineResponse line) {
        if (line == null) return false;
        String s = nz(line.getLineStatus(), "QUOTED").toUpperCase(Locale.ROOT);
        return "QUOTED".equals(s) || "CLARIFICATION".equals(s) || "ALTERNATIVE".equals(s);
    }

    // ── Continuous renderer ─────────────────────────────────────────────────

    private byte[] generate(Ctx ctx) {
        byte[] adminJpeg = toJpeg(ctx.adminSigBytes());
        byte[] subJpeg = ctx.executed() ? toJpeg(ctx.subSigBytes()) : null;

        Doc doc = new Doc();
        drawHeader(doc, ctx);
        drawMeta(doc, ctx);
        drawLetter(doc, ctx);
        drawAwardCard(doc, ctx);
        drawBoq(doc, ctx);
        drawCommercial(doc, ctx);
        // Start T&C on a fresh page unless we already just broke (avoid blank page)
        doc.breakBeforeSection();
        drawTerms(doc);
        drawSignatures(doc, ctx, adminJpeg != null, subJpeg != null);
        drawClosing(doc);

        List<String> pages = doc.finish(ctx.ref());
        return assemble(pages, adminJpeg, dims(adminJpeg), subJpeg, dims(subJpeg));
    }

    /** Mutable multi-page content stream builder with continuous Y flow. */
    private static final class Doc {
        private final List<String> pages = new ArrayList<>();
        private StringBuilder cur = new StringBuilder("q\n");
        private float y = PAGE_H - MT;

        float y() { return y; }

        void ensure(float need) {
            if (y - need < MB && cur.length() > 4) {
                newPage();
            }
        }

        void newPage() {
            if (cur.length() <= 4) {
                y = PAGE_H - MT;
                return;
            }
            cur.append("Q\n");
            pages.add(cur.toString());
            cur = new StringBuilder("q\n");
            y = PAGE_H - MT;
        }

        /** Start T&C on a new page only when still mid-page — never after an orphaned spill. */
        void breakBeforeSection() {
            float fromTop = (PAGE_H - MT) - y;
            // Already near top of a page (e.g. last commercial row spilled here) — stay.
            if (fromTop <= 100f) {
                return;
            }
            newPage();
        }

        void append(String ops) { cur.append(ops); }

        void move(float dy) { y -= dy; }

        void setY(float ny) { y = ny; }

        List<String> finish(String ref) {
            cur.append("Q\n");
            pages.add(cur.toString());
            int total = pages.size();
            List<String> out = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                out.add(pages.get(i) + footer(ref, i + 1, total));
            }
            return out;
        }
    }

    private void drawHeader(Doc doc, Ctx ctx) {
        float h = 58f;
        doc.ensure(h);
        float top = doc.y();
        float bottom = top - h;
        doc.append("0.122 0.227 0.204 rg " + ML + " " + fmt(bottom) + " " + CW + " " + h + " re f\n");
        doc.append("1 1 1 rg\n");
        text(doc, "/F1", 9, ML + 14, top - 16, "SUBCONTRACT AWARD");
        text(doc, "/F2", FONT_TITLE, ML + 14, top - 36, "JCT Contracting");
        text(doc, "/F1", 9.5f, ML + 14, top - 52, "Premium Fit-Out & Interior Solutions");
        float rx = ML + CW - 155;
        doc.append("0.784 0.663 0.494 rg\n");
        text(doc, "/F2", 11, rx, top - 18, "Cover Letter");
        doc.append("1 1 1 rg\n");
        text(doc, "/F1", 10, rx, top - 34, ctx.ref());
        text(doc, "/F1", 9, rx, top - 48, ctx.statusLabel());
        doc.setY(bottom);
    }

    private void drawMeta(Doc doc, Ctx ctx) {
        float h = 48f;
        doc.ensure(h);
        float top = doc.y();
        float bottom = top - h;
        doc.append("0.969 0.961 0.949 rg " + ML + " " + fmt(bottom) + " " + CW + " " + h + " re f\n");
        doc.append("0.898 0.882 0.855 RG " + ML + " " + fmt(bottom) + " " + CW + " " + h + " re S\n");

        String date = ctx.award() != null && ctx.award().getAwardedAt() != null
                ? ctx.award().getAwardedAt().format(DATE_META) : "-";
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", FONT_META, ML + 12, top - 16, "Date: ");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_META, ML + 42, top - 16, date);
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", FONT_META, ML + 12, top - 32, "Awarded value (excl. VAT): ");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_META, ML + 138, top - 32, "AED " + money(ctx.awardValue()));

        float rx = ML + CW / 2 + 10;
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", FONT_META, rx, top - 14, "Subcontractor: ");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_META, rx + 72, top - 14, clip(ctx.orgName(), 34));
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", FONT_META, rx, top - 28, "Project: ");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_META, rx + 42, top - 28, clip(ctx.projectName(), 38));
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", FONT_META, rx, top - 42, "Location: ");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_META, rx + 48, top - 42, clip(ctx.location(), 36));
        doc.setY(bottom - GAP_SUB);
    }

    private void drawLetter(Doc doc, Ctx ctx) {
        doc.ensure(50);
        label(doc, "TO");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F2", 11, ML, doc.y(), clip(ctx.orgName(), 70));
        doc.move(13);
        if (StringUtils.hasText(ctx.tradeLabel())) {
            doc.append("0.22 0.26 0.32 rg\n");
            text(doc, "/F1", FONT_BODY, ML, doc.y(), clip(ctx.tradeLabel(), 78));
            doc.move(13);
        }
        doc.move(4);
        label(doc, "SUBJECT");
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F2", 11.5f, ML, doc.y(),
                clip("SUBCONTRACT AWARD - " + ctx.packageName().toUpperCase(Locale.ROOT), 72));
        doc.move(16);

        for (String para : AWARD_BODY) {
            doc.ensure(36);
            float after = wrap(doc, para, ML, doc.y(), CW, FONT_BODY, LEAD_BODY);
            doc.setY(after - GAP_PARA);
        }
        doc.move(GAP_SUB);
    }

    private void drawAwardCard(Doc doc, Ctx ctx) {
        float h = 48f;
        doc.ensure(h + GAP_MAJOR);
        float top = doc.y();
        float bottom = top - h;
        doc.append("0.969 0.961 0.949 rg " + ML + " " + fmt(bottom) + " " + CW + " " + h + " re f\n");
        doc.append("0.122 0.227 0.204 RG " + ML + " " + fmt(bottom) + " " + CW + " " + h + " re S\n");
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", 8.5f, ML + 14, top - 14, "AWARDED SUBCONTRACT VALUE");
        doc.append("0.122 0.227 0.204 rg\n");
        text(doc, "/F2", 16, ML + 14, top - 32, "AED " + money(ctx.awardValue()));
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", 8.5f, ML + 14, top - 46, "Exclusive of Value Added Tax - from agreed BOQ / award");
        doc.setY(bottom - GAP_MAJOR);
    }

    private void drawBoq(Doc doc, Ctx ctx) {
        if (ctx.boqLines().isEmpty()) return;

        doc.ensure(48);
        label(doc, "AGREED PRICED BOQ - AWARDED LINES");
        doc.append("0.42 0.42 0.42 rg\n");
        float after = wrap(doc,
                "Frozen snapshot from the winning quote. Included lines below form the priced subcontract scope.",
                ML, doc.y(), CW, 8.5f, 11f);
        doc.setY(after - 6);

        drawBoqHeader(doc);

        for (ScAwardBoqLineResponse line : ctx.boqLines()) {
            if (!isIncluded(line)) continue;
            float rowH = estimateBoqRow(line);
            doc.ensure(rowH + 6);
            if (justStartedPage(doc)) {
                drawBoqHeader(doc);
            }
            drawBoqRow(doc, line, false);
        }

        // Totals block: subtotal / adjustment / final
        BigDecimal subtotal = ctx.boqSubtotal();
        BigDecimal award = ctx.awardValue() != null ? ctx.awardValue() : BigDecimal.ZERO;
        BigDecimal adjustment = award.subtract(subtotal);
        boolean differ = adjustment.abs().compareTo(new BigDecimal("0.01")) > 0;

        doc.ensure(differ ? 48f : 20f);
        drawTotalRow(doc, "Priced BOQ Subtotal", "AED " + money(subtotal), false);
        if (differ) {
            drawTotalRow(doc, "Commercial Adjustment", "AED " + money(adjustment), false);
            drawTotalRow(doc, "Final Award Value", "AED " + money(award) + " excl. VAT", true);
            if (ctx.award() != null && StringUtils.hasText(ctx.award().getAwardValueReason())) {
                doc.append("0.42 0.42 0.42 rg\n");
                after = wrap(doc, "Adjustment reason: " + ctx.award().getAwardValueReason().trim(),
                        ML + 6, doc.y(), CW - 12, 8.5f, 11f);
                doc.setY(after - 6);
            }
        }

        for (ScAwardBoqLineResponse line : ctx.boqLines()) {
            if (isIncluded(line)) continue;
            doc.ensure(18);
            drawBoqRow(doc, line, true);
        }
        doc.move(GAP_SUB);
    }

    private void drawTotalRow(Doc doc, String label, String value, boolean strong) {
        float h = 15f;
        float top = doc.y();
        if (strong) {
            doc.append("0.122 0.227 0.204 rg " + ML + " " + fmt(top - h) + " " + CW + " " + h + " re f\n");
            doc.append("1 1 1 rg\n");
        } else {
            doc.append("0.969 0.961 0.949 rg " + ML + " " + fmt(top - h) + " " + CW + " " + h + " re f\n");
            doc.append("0.07 0.09 0.15 rg\n");
        }
        String font = strong ? "/F2" : "/F1";
        text(doc, font, FONT_TABLE, ML + 8, top - 11, label);
        text(doc, font, FONT_TABLE, ML + CW - 140, top - 11, value);
        doc.setY(top - h - 2);
    }

    private boolean justStartedPage(Doc doc) {
        return doc.y() >= PAGE_H - MT - 1;
    }

    private void drawBoqHeader(Doc doc) {
        float h = 16f;
        doc.ensure(h + 4);
        float top = doc.y();
        doc.append("0.93 0.92 0.90 rg " + ML + " " + fmt(top - h) + " " + CW + " " + h + " re f\n");
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F2", FONT_TABLE, ML + 4, top - 11, "Code");
        text(doc, "/F2", FONT_TABLE, ML + 48, top - 11, "Description");
        text(doc, "/F2", FONT_TABLE, ML + 275, top - 11, "Unit");
        text(doc, "/F2", FONT_TABLE, ML + 308, top - 11, "Qty");
        text(doc, "/F2", FONT_TABLE, ML + 342, top - 11, "Rate (AED)");
        text(doc, "/F2", FONT_TABLE, ML + 402, top - 11, "Amount (AED)");
        text(doc, "/F2", FONT_TABLE, ML + 462, top - 11, "Status");
        doc.setY(top - h - 2);
    }

    private float estimateBoqRow(ScAwardBoqLineResponse line) {
        int n = Math.min(Math.max(wrapText(nz(line.getDescription(), "-"), 40).size(), 1), 3);
        return 8 + n * LEAD_TABLE;
    }

    private void drawBoqRow(Doc doc, ScAwardBoqLineResponse line, boolean muted) {
        List<String> desc = wrapText(nz(line.getDescription(), "-"), 40);
        if (desc.size() > 3) desc = new ArrayList<>(desc.subList(0, 3));
        float pad = 4f;
        float rowH = pad + desc.size() * LEAD_TABLE + pad;
        float top = doc.y() - pad;
        if (muted) doc.append("0.55 0.55 0.55 rg\n");
        else doc.append("0.12 0.16 0.22 rg\n");
        text(doc, "/F1", FONT_TABLE, ML + 4, top, clip(nz(line.getSectionCode(), "-"), 8));
        text(doc, "/F1", FONT_TABLE, ML + 48, top, desc.get(0));
        if (!muted) {
            text(doc, "/F1", FONT_TABLE, ML + 275, top, clip(nz(line.getUnit(), "-"), 5));
            text(doc, "/F1", FONT_TABLE, ML + 308, top, qty(line.getQuantity()));
            text(doc, "/F1", FONT_TABLE, ML + 342, top, money(line.getRate()));
            text(doc, "/F1", FONT_TABLE, ML + 402, top, money(line.getAmount()));
        } else {
            text(doc, "/F1", FONT_TABLE, ML + 275, top, clip(nz(line.getRemarks(), "Not included in awarded scope"), 26));
        }
        text(doc, "/F1", FONT_TABLE, ML + 462, top, clip(nz(line.getLineStatus(), muted ? "EXCLUDED" : "QUOTED"), 10));
        for (int i = 1; i < desc.size(); i++) {
            text(doc, "/F1", FONT_TABLE, ML + 48, top - i * LEAD_TABLE, desc.get(i));
        }
        float bottom = doc.y() - rowH;
        doc.append("0.94 0.93 0.91 RG " + ML + " " + fmt(bottom) + " m " + (ML + CW) + " " + fmt(bottom) + " l S\n");
        doc.setY(bottom - 1);
    }

    private void drawCommercial(Doc doc, Ctx ctx) {
        String retention = ctx.pkg() != null && ctx.pkg().getRetentionPct() != null
                ? ctx.pkg().getRetentionPct().stripTrailingZeros().toPlainString() + "% retention · DLP 12 months"
                : "DLP 12 months (JCT standard)";
        String programme;
        if (ctx.pkg() != null && (ctx.pkg().getPlannedStart() != null || ctx.pkg().getPlannedFinish() != null)) {
            programme = (ctx.pkg().getPlannedStart() != null ? "Start " + ctx.pkg().getPlannedStart() : "")
                    + (ctx.pkg().getPlannedStart() != null && ctx.pkg().getPlannedFinish() != null ? " · " : "")
                    + (ctx.pkg().getPlannedFinish() != null ? "Finish " + ctx.pkg().getPlannedFinish() : "");
        } else {
            programme = "Refer to package programme / master schedule";
        }
        String inclusions = nz(ctx.pkg() != null ? ctx.pkg().getTenderDescription() : null,
                ctx.tradeLabel(), "As tendered trade package scope");
        String exclusions = nz(ctx.exclusionsText(), "As recorded on awarded quote (if any)");

        String[][] rows = {
                {"Payment terms", nz(ctx.pkg() != null ? ctx.pkg().getPaymentTerms() : null, "Per JCT standard payment schedule")},
                {"Retention / DLP", retention},
                {"LD / delay recovery", nz(ctx.pkg() != null ? ctx.pkg().getLdTerms() : null, "Per JCT delay / non-payment recovery clauses")},
                {"Programme", programme},
                {"Scope inclusions", inclusions},
                {"Scope exclusions", exclusions},
        };
        // Keep the commercial table together — avoid orphaning the last row on a blank page
        float tableH = 18f;
        for (String[] row : rows) {
            tableH += 6 + Math.max(1, wrapText(row[1], 68).size()) * LEAD_TABLE + 4;
        }
        if (doc.y() - tableH < MB) {
            doc.newPage();
        }
        label(doc, "PACKAGE COMMERCIAL TERMS");
        for (String[] row : rows) {
            int valueLines = Math.max(1, wrapText(row[1], 68).size());
            float rowH = 6 + valueLines * LEAD_TABLE;
            doc.ensure(rowH + 4);
            float top = doc.y() - 2;
            doc.append("0.42 0.42 0.42 rg\n");
            text(doc, "/F2", FONT_TABLE, ML, top, clip(row[0], 22));
            doc.append("0.12 0.16 0.22 rg\n");
            float after = wrap(doc, row[1], ML + 125, top, CW - 125, FONT_TABLE, LEAD_TABLE);
            float bottom = Math.min(after, top - 11f) - 3;
            doc.append("0.94 0.93 0.91 RG " + ML + " " + fmt(bottom) + " m " + (ML + CW) + " " + fmt(bottom) + " l S\n");
            doc.setY(bottom - 2);
        }
        doc.move(GAP_SUB);
    }

    private void drawTerms(Doc doc) {
        label(doc, "JCT TERMS & CONDITIONS - SUMMARY");
        for (String[] row : TERMS_SUMMARY) {
            int valueLines = Math.max(1, wrapText(row[1], 66).size());
            float rowH = 8 + valueLines * LEAD_TABLE;
            doc.ensure(rowH + 4);
            float top = doc.y() - 3;
            doc.append("0.42 0.42 0.42 rg\n");
            text(doc, "/F2", FONT_TABLE, ML, top, clip(row[0], 22));
            doc.append("0.12 0.16 0.22 rg\n");
            float after = wrap(doc, row[1], ML + 125, top, CW - 125, FONT_TABLE, LEAD_TABLE);
            float bottom = Math.min(after, top - 11f) - 3;
            doc.append("0.94 0.93 0.91 RG " + ML + " " + fmt(bottom) + " m " + (ML + CW) + " " + fmt(bottom) + " l S\n");
            doc.setY(bottom - 2);
        }
        doc.move(GAP_SUB);
        for (int i = 0; i < TERMS_CLAUSES.length; i++) {
            String clause = (i + 1) + ". " + TERMS_CLAUSES[i];
            int lines = Math.max(1, wrapText(clause, 90).size());
            doc.ensure(lines * LEAD_BODY + 6);
            doc.append("0.22 0.26 0.32 rg\n");
            float after = wrap(doc, clause, ML + 4, doc.y(), CW - 4, FONT_BODY, LEAD_BODY);
            doc.setY(after - GAP_PARA);
        }
        doc.ensure(16);
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", 8.5f, ML, doc.y(), "Full JCT Terms and Conditions form part of this agreement.");
        doc.move(GAP_MAJOR);
    }

    private void drawSignatures(Doc doc, Ctx ctx, boolean hasAdminImg, boolean hasSubImg) {
        float boxH = 124f;
        // Keep label + both cards together (do not split signature block)
        doc.ensure(boxH + 28);
        label(doc, "EXECUTION");
        float top = doc.y();
        float bottom = top - boxH;
        float gap = 12f;
        float boxW = (CW - gap) / 2f;

        drawSigBox(doc, ML, bottom, boxW, boxH, "MAIN CONTRACTOR - JCT",
                ctx.adminSignedAt() != null,
                nz(ctx.adminSignerName(), "Admin"),
                nz(ctx.adminSignerTitle(), ""),
                ctx.adminSignedAt() != null ? ctx.adminSignedAt().format(DATE_TIME) : null,
                hasAdminImg, true);

        drawSigBox(doc, ML + boxW + gap, bottom, boxW, boxH, "SUBCONTRACTOR",
                ctx.executed(),
                ctx.executed() ? nz(ctx.subSignerName(), ctx.orgName()) : null,
                ctx.executed() ? nz(ctx.subSignerTitle(), "") : null,
                ctx.executed() && ctx.subSignedAt() != null ? ctx.subSignedAt().format(DATE_TIME) : null,
                hasSubImg && ctx.executed(), false);

        doc.setY(bottom - GAP_SUB);
    }

    private void drawSigBox(Doc doc, float x, float bottom, float w, float h,
                            String title, boolean signed, String name, String role,
                            String when, boolean hasImg, boolean admin) {
        doc.append("0.969 0.961 0.949 rg " + fmt(x) + " " + fmt(bottom) + " " + fmt(w) + " " + fmt(h) + " re f\n");
        doc.append("0.898 0.882 0.855 RG " + fmt(x) + " " + fmt(bottom) + " " + fmt(w) + " " + fmt(h) + " re S\n");
        float top = bottom + h;
        doc.append("0.122 0.227 0.204 rg\n");
        text(doc, "/F2", 9.5f, x + 10, top - 14, title);
        if (signed) {
            doc.append("0.07 0.09 0.15 rg\n");
            text(doc, "/F1", FONT_BODY, x + 10, top - 30, clip(name, 36));
            if (StringUtils.hasText(role)) {
                doc.append("0.42 0.42 0.42 rg\n");
                text(doc, "/F1", 8.5f, x + 10, top - 42, clip(role, 36));
            }
            doc.append("0.42 0.42 0.42 rg\n");
            text(doc, "/F1", 8.5f, x + 10, top - 54, "Signed " + nz(when, "-"));
            if (hasImg) {
                String img = admin ? "/Im1" : "/Im2";
                doc.append("q 120 0 0 44 " + fmt(x + 10) + " " + fmt(bottom + 12) + " cm " + img + " Do Q\n");
            }
        } else {
            doc.append("0.42 0.42 0.42 rg\n");
            text(doc, "/F1", FONT_BODY, x + 10, top - 44,
                    admin ? "Waiting for admin signature" : "[ Waiting for Subcontractor signature ]");
        }
    }

    private void drawClosing(Doc doc) {
        doc.ensure(58);
        doc.append("0.07 0.09 0.15 rg\n");
        text(doc, "/F1", FONT_BODY, ML, doc.y(), "Respectfully,");
        doc.move(14);
        text(doc, "/F2", FONT_BODY, ML, doc.y(), "JCT Contracting");
        doc.move(12);
        text(doc, "/F1", FONT_BODY, ML, doc.y(), "Grigoris Georgiou");
        doc.move(11);
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F1", 8.5f, ML, doc.y(), "Projects Director");
        doc.move(14);
        doc.append("0.122 0.227 0.204 rg\n");
        text(doc, "/F2", FONT_BODY, ML, doc.y(), "Thank you for your business!");
        doc.move(8);
    }

    private void label(Doc doc, String text) {
        doc.append("0.42 0.42 0.42 rg\n");
        text(doc, "/F2", FONT_SECTION, ML, doc.y(), text);
        doc.move(12);
    }

    private void text(Doc doc, String font, float size, float x, float y, String value) {
        doc.append("BT " + font + " " + size + " Tf " + fmt(x) + " " + fmt(y)
                + " Td (" + pdf(value) + ") Tj ET\n");
    }

    private float wrap(Doc doc, String text, float x, float startY, float maxWidth,
                       float fontSize, float leading) {
        int maxChars = Math.max(20, (int) (maxWidth / (fontSize * 0.50f)));
        List<String> lines = wrapText(text, maxChars);
        float y = startY;
        for (String line : lines) {
            doc.append("BT /F1 " + fontSize + " Tf " + fmt(x) + " " + fmt(y)
                    + " Td (" + pdf(line) + ") Tj ET\n");
            y -= leading;
        }
        return y;
    }

    private static String footer(String ref, int page, int total) {
        return "0.80 0.80 0.80 RG " + ML + " " + FOOTER_Y + " m " + (ML + CW) + " " + FOOTER_Y + " l S\n"
                + "0.45 0.45 0.45 rg BT /F1 " + FONT_FOOTER + " Tf " + ML + " " + (FOOTER_Y - 11)
                + " Td (JCT Contracting | Subcontract Agreement | " + pdf(ref)
                + " | Page " + page + " of " + total + ") Tj ET\n";
    }

    // ── PDF assembly ────────────────────────────────────────────────────────

    private byte[] assemble(List<String> pageContents, byte[] adminJpeg, ImageDim adminDim,
                            byte[] subJpeg, ImageDim subDim) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Long> xref = new ArrayList<>();
            write(out, "%PDF-1.4\n");
            int next = 1;
            int catalog = next++;
            int pagesObj = next++;
            int font1 = next++;
            int font2 = next++;
            Integer im1 = adminJpeg != null ? next++ : null;
            Integer im2 = subJpeg != null ? next++ : null;
            int n = pageContents.size();
            int[] pageIds = new int[n];
            int[] contentIds = new int[n];
            for (int i = 0; i < n; i++) {
                pageIds[i] = next++;
                contentIds[i] = next++;
            }

            xref.add((long) out.size());
            write(out, catalog + " 0 obj\n<< /Type /Catalog /Pages " + pagesObj + " 0 R >>\nendobj\n");
            StringBuilder kids = new StringBuilder("[");
            for (int id : pageIds) kids.append(id).append(" 0 R ");
            kids.append("]");
            xref.add((long) out.size());
            write(out, pagesObj + " 0 obj\n<< /Type /Pages /Count " + n + " /Kids " + kids + " >>\nendobj\n");
            xref.add((long) out.size());
            write(out, font1 + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            xref.add((long) out.size());
            write(out, font2 + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");
            if (im1 != null) {
                xref.add((long) out.size());
                writeImage(out, im1, adminJpeg, adminDim);
            }
            if (im2 != null) {
                xref.add((long) out.size());
                writeImage(out, im2, subJpeg, subDim);
            }
            StringBuilder res = new StringBuilder();
            res.append("<< /Font << /F1 ").append(font1).append(" 0 R /F2 ").append(font2)
                    .append(" 0 R >> /XObject << ");
            if (im1 != null) res.append("/Im1 ").append(im1).append(" 0 R ");
            if (im2 != null) res.append("/Im2 ").append(im2).append(" 0 R ");
            res.append(">> >>");

            for (int i = 0; i < n; i++) {
                xref.add((long) out.size());
                write(out, pageIds[i] + " 0 obj\n<< /Type /Page /Parent " + pagesObj
                        + " 0 R /MediaBox [0 0 595 842] /Resources " + res
                        + " /Contents " + contentIds[i] + " 0 R >>\nendobj\n");
                byte[] bytes = pageContents.get(i).getBytes(StandardCharsets.ISO_8859_1);
                xref.add((long) out.size());
                write(out, contentIds[i] + " 0 obj\n<< /Length " + bytes.length + " >>\nstream\n");
                out.write(bytes);
                write(out, "\nendstream\nendobj\n");
            }
            long startXref = out.size();
            write(out, "xref\n0 " + (xref.size() + 1) + "\n");
            write(out, "0000000000 65535 f \n");
            for (Long pos : xref) write(out, String.format("%010d 00000 n \n", pos));
            write(out, "trailer\n<< /Size " + (xref.size() + 1) + " /Root " + catalog + " 0 R >>\n");
            write(out, "startxref\n" + startXref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void writeImage(ByteArrayOutputStream out, int id, byte[] jpeg, ImageDim dim) throws IOException {
        int w = dim != null && dim.w > 0 ? dim.w : 200;
        int h = dim != null && dim.h > 0 ? dim.h : 80;
        write(out, id + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpeg.length + " >>\nstream\n");
        out.write(jpeg);
        write(out, "\nendstream\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private record ImageDim(int w, int h) {}

    private ImageDim dims(byte[] jpeg) {
        if (jpeg == null) return new ImageDim(200, 80);
        try (ByteArrayInputStream in = new ByteArrayInputStream(jpeg)) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) return new ImageDim(img.getWidth(), img.getHeight());
        } catch (Exception ignored) {}
        return new ImageDim(200, 80);
    }

    private byte[] toJpeg(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Signature image conversion failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Text utils ──────────────────────────────────────────────────────────

    private static String pdf(String value) {
        if (value == null) return "";
        String s = value
                .replace('\u2014', '-').replace('\u2013', '-').replace('\u2212', '-')
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"')
                .replace("\u2026", "...").replace('\u00A0', ' ')
                .replace("·", "-").replace("د.إ", "AED");
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') out.append(' ');
            else if ((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 255)) out.append(ch);
        }
        return out.toString().replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            lines.add("");
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) line.append(word);
            else if (line.length() + 1 + word.length() <= maxChars) line.append(' ').append(word);
            else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static String clip(String text, int max) {
        if (!StringUtils.hasText(text)) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)) + "...";
    }

    private static String money(BigDecimal value) {
        if (value == null) return "0.00";
        return String.format(Locale.US, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String qty(BigDecimal value) {
        if (value == null) return "-";
        return value.stripTrailingZeros().toPlainString();
    }

    private static String nz(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return "";
    }

    private static String fmt(float v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
