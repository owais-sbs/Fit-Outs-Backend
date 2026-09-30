package com.fitouts.profitloss.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.profitloss.api.CompanyPnlResponse;
import com.fitouts.profitloss.api.ProjectPnlResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnlExportService {

    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        MONEY = new DecimalFormat("#,##0.00", symbols);
    }

    private final PnlCalculationService pnlCalculationService;

    public String companyCsv(String yearMonth) {
        CompanyPnlResponse pnl = pnlCalculationService.getCompanyPnl(yearMonth);
        StringBuilder sb = new StringBuilder();
        sb.append("period,projectId,projectName,contractValue,materialCost,labourCost,scCertifiedCost,")
                .append("variationCost,overheadAllocated,totalCost,margin,marginPercent,")
                .append("originalContractValue,originalEstimatedCost,marginVsOriginalEstimate\n");
        for (ProjectPnlResponse row : pnl.getProjects()) {
            appendRow(sb, row);
        }
        return sb.toString();
    }

    public String projectCsv(Long projectId) {
        ProjectPnlResponse row = pnlCalculationService.getProjectPnl(projectId);
        StringBuilder sb = new StringBuilder();
        sb.append("period,projectId,projectName,contractValue,materialCost,labourCost,scCertifiedCost,")
                .append("variationCost,overheadAllocated,totalCost,margin,marginPercent,")
                .append("originalContractValue,originalEstimatedCost,marginVsOriginalEstimate\n");
        appendRow(sb, row);
        return sb.toString();
    }

    public byte[] companyPdf(String yearMonth) {
        CompanyPnlResponse pnl = pnlCalculationService.getCompanyPnl(yearMonth);
        return renderPdf("Company Profit & Loss", pnl, true);
    }

    public byte[] projectPdf(Long projectId) {
        ProjectPnlResponse row = pnlCalculationService.getProjectPnl(projectId);
        CompanyPnlResponse wrap = CompanyPnlResponse.builder()
                .periodYearMonth(row.getPeriodYearMonth())
                .contractValue(row.getContractValue())
                .materialCost(row.getMaterialCost())
                .labourCost(row.getLabourCost())
                .scCertifiedCost(row.getScCertifiedCost())
                .variationCost(row.getVariationCost())
                .overheadAllocated(row.getOverheadAllocated())
                .totalCost(row.getTotalCost())
                .margin(row.getMargin())
                .marginPercent(row.getMarginPercent())
                .projects(List.of(row))
                .build();
        String title = "Project Profit & Loss";
        if (StringUtils.hasText(row.getProjectName())) {
            title = title + " - " + row.getProjectName();
        }
        return renderPdf(title, wrap, false);
    }

    private void appendRow(StringBuilder sb, ProjectPnlResponse row) {
        sb.append(csv(row.getPeriodYearMonth())).append(',')
                .append(row.getProjectId()).append(',')
                .append(csv(row.getProjectName())).append(',')
                .append(num(row.getContractValue())).append(',')
                .append(num(row.getMaterialCost())).append(',')
                .append(num(row.getLabourCost())).append(',')
                .append(num(row.getScCertifiedCost())).append(',')
                .append(num(row.getVariationCost())).append(',')
                .append(num(row.getOverheadAllocated())).append(',')
                .append(num(row.getTotalCost())).append(',')
                .append(num(row.getMargin())).append(',')
                .append(num(row.getMarginPercent())).append(',')
                .append(num(row.getOriginalContractValue())).append(',')
                .append(num(row.getOriginalEstimatedCost())).append(',')
                .append(num(row.getMarginVsOriginalEstimate())).append('\n');
    }

    private byte[] renderPdf(String title, CompanyPnlResponse pnl, boolean companyWide) {
        List<String> pages = new ArrayList<>();
        List<ProjectPnlResponse> projects = pnl.getProjects() != null ? pnl.getProjects() : List.of();

        if (!companyWide && projects.size() == 1) {
            pages.add(buildProjectDetailPage(title, pnl, projects.get(0)));
        } else {
            int rowsPerPage = 22;
            int totalPages = Math.max(1, (int) Math.ceil(projects.size() / (double) rowsPerPage));
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int from = pageIndex * rowsPerPage;
                int to = Math.min(from + rowsPerPage, projects.size());
                pages.add(buildCompanyTablePage(
                        title, pnl, projects.subList(from, to), pageIndex + 1, totalPages, pageIndex == 0));
            }
            if (pages.isEmpty()) {
                pages.add(buildCompanyTablePage(title, pnl, List.of(), 1, 1, true));
            }
        }
        return assembleMultiPagePdf(pages);
    }

    private String buildProjectDetailPage(String title, CompanyPnlResponse pnl, ProjectPnlResponse row) {
        StringBuilder c = new StringBuilder();
        c.append("q\n");

        // Header
        c.append("0.122 0.227 0.204 rg 0 762 595 80 re f\n");
        c.append("1 1 1 rg\n");
        c.append("BT /F1 9 Tf 40 820 Td (PROFIT & LOSS REPORT) Tj ET\n");
        c.append("BT /F2 16 Tf 40 798 Td (").append(pdf(clip(title, 62))).append(") Tj ET\n");
        c.append("BT /F1 9 Tf 40 780 Td (Period: ")
                .append(pdf(nullSafe(row.getPeriodYearMonth(), "Current")))
                .append("  |  Generated ")
                .append(pdf(LocalDate.now().format(DATE_LONG)))
                .append(") Tj ET\n");

        // Summary strip
        float y = 730;
        c.append("0.969 0.961 0.949 rg 40 ").append(y - 58).append(" 515 62 re f\n");
        c.append("0.898 0.882 0.855 RG 40 ").append(y - 58).append(" 515 62 re S\n");

        drawKpi(c, 52, y - 12, "CONTRACT VALUE", money(row.getContractValue()));
        drawKpi(c, 185, y - 12, "TOTAL COST", money(row.getTotalCost()));
        drawKpi(c, 318, y - 12, "MARGIN", money(row.getMargin()));
        drawKpi(c, 450, y - 12, "MARGIN %", percent(row.getMarginPercent()));

        y -= 90;
        y = drawSectionTitle(c, 40, y, "COST BREAKDOWN");
        y -= 4;

        String[][] costRows = {
                {"Materials", money(row.getMaterialCost())},
                {"SC certified", money(row.getScCertifiedCost())},
                {"Variation cost", money(row.getVariationCost())},
                {"Overhead allocated", money(row.getOverheadAllocated())},
                {"Labour (not tracked in v1)", money(row.getLabourCost())},
                {"Total cost", money(row.getTotalCost())},
        };
        for (String[] costRow : costRows) {
            y = drawKeyValueRow(c, y, costRow[0], costRow[1]);
        }

        y -= 16;
        y = drawSectionTitle(c, 40, y, "MARGIN VS ORIGINAL ESTIMATE");
        y -= 4;
        String[][] estimateRows = {
                {"Original contract value", money(row.getOriginalContractValue())},
                {"Original estimated cost",
                        row.getOriginalEstimatedCost() != null ? money(row.getOriginalEstimatedCost()) : "N/A"},
                {"Margin vs original",
                        row.getMarginVsOriginalEstimate() != null ? money(row.getMarginVsOriginalEstimate()) : "N/A"},
        };
        for (String[] estimateRow : estimateRows) {
            y = drawKeyValueRow(c, y, estimateRow[0], estimateRow[1]);
        }

        y -= 20;
        c.append("0.42 0.42 0.42 rg\n");
        y = drawWrapped(c,
                "Amounts are in AED. Labour cost is not tracked in v1 and is shown as 0. "
                        + "Figures reflect the latest calculated P&L snapshot for this project.",
                40, y, 515, 8, 11);

        appendFooter(c, 1, 1);
        c.append("Q\n");
        return c.toString();
    }

    private String buildCompanyTablePage(
            String title,
            CompanyPnlResponse pnl,
            List<ProjectPnlResponse> rows,
            int pageNum,
            int totalPages,
            boolean showSummary) {
        StringBuilder c = new StringBuilder();
        c.append("q\n");

        c.append("0.122 0.227 0.204 rg 0 790 595 52 re f\n");
        c.append("1 1 1 rg\n");
        c.append("BT /F1 9 Tf 40 822 Td (COMPANY PROFIT & LOSS) Tj ET\n");
        c.append("BT /F2 15 Tf 40 802 Td (").append(pdf(clip(title, 55))).append(") Tj ET\n");
        c.append("0.784 0.663 0.494 rg BT /F1 9 Tf 400 812 Td (Period: ")
                .append(pdf(nullSafe(pnl.getPeriodYearMonth(), "-")))
                .append(") Tj ET\n");

        float y = 760;
        if (showSummary) {
            c.append("0.969 0.961 0.949 rg 40 ").append(y - 50).append(" 515 54 re f\n");
            c.append("0.898 0.882 0.855 RG 40 ").append(y - 50).append(" 515 54 re S\n");
            drawKpi(c, 52, y - 10, "CONTRACT", money(pnl.getContractValue()));
            drawKpi(c, 185, y - 10, "TOTAL COST", money(pnl.getTotalCost()));
            drawKpi(c, 318, y - 10, "MARGIN", money(pnl.getMargin()));
            drawKpi(c, 450, y - 10, "MARGIN %", percent(pnl.getMarginPercent()));
            y -= 72;
            c.append("0.42 0.42 0.42 rg BT /F1 8 Tf 40 ").append(y)
                    .append(" Td (Labour cost is not tracked in v1 and is shown as 0. Amounts in AED.) Tj ET\n");
            y -= 18;
        }

        y = drawSectionTitle(c, 40, y, "PROJECT BREAKDOWN");
        y -= 2;

        // Column headers
        c.append("0.122 0.227 0.204 rg 40 ").append(y - 4).append(" 515 18 re f\n");
        c.append("1 1 1 rg\n");
        drawTableHeader(c, y + 2);
        y -= 18;

        boolean alt = false;
        for (ProjectPnlResponse row : rows) {
            if (y < 70) {
                break;
            }
            if (alt) {
                c.append("0.969 0.961 0.949 rg 40 ").append(y - 4).append(" 515 14 re f\n");
            }
            drawTableRow(c, y, row);
            y -= 14;
            alt = !alt;
        }

        if (rows.isEmpty()) {
            c.append("0.42 0.42 0.42 rg BT /F1 9 Tf 40 ").append(y)
                    .append(" Td (No P&L project rows for this period.) Tj ET\n");
        }

        appendFooter(c, pageNum, totalPages);
        c.append("Q\n");
        return c.toString();
    }

    private void drawTableHeader(StringBuilder c, float y) {
        c.append("BT /F2 7 Tf 44 ").append(y).append(" Td (PROJECT) Tj ET\n");
        c.append("BT /F2 7 Tf 190 ").append(y).append(" Td (CONTRACT) Tj ET\n");
        c.append("BT /F2 7 Tf 250 ").append(y).append(" Td (MATERIALS) Tj ET\n");
        c.append("BT /F2 7 Tf 310 ").append(y).append(" Td (SC) Tj ET\n");
        c.append("BT /F2 7 Tf 355 ").append(y).append(" Td (VARIATION) Tj ET\n");
        c.append("BT /F2 7 Tf 415 ").append(y).append(" Td (OVERHEAD) Tj ET\n");
        c.append("BT /F2 7 Tf 475 ").append(y).append(" Td (TOTAL) Tj ET\n");
        c.append("BT /F2 7 Tf 520 ").append(y).append(" Td (MARGIN) Tj ET\n");
    }

    private void drawTableRow(StringBuilder c, float y, ProjectPnlResponse row) {
        c.append("0.12 0.16 0.22 rg\n");
        c.append("BT /F1 7 Tf 44 ").append(y).append(" Td (")
                .append(pdf(clip(nullSafe(row.getProjectName(), "-"), 26))).append(") Tj ET\n");
        rightText(c, money(row.getContractValue()), 242, y);
        rightText(c, money(row.getMaterialCost()), 302, y);
        rightText(c, money(row.getScCertifiedCost()), 347, y);
        rightText(c, money(row.getVariationCost()), 407, y);
        rightText(c, money(row.getOverheadAllocated()), 467, y);
        rightText(c, money(row.getTotalCost()), 512, y);
        rightText(c, money(row.getMargin()), 555, y);
    }

    private void rightText(StringBuilder c, String text, float rightX, float y) {
        // Approximate Helvetica width ~0.5 * fontSize per char at size 7
        float approxWidth = text.length() * 3.6f;
        float x = rightX - approxWidth;
        c.append("BT /F1 7 Tf ").append(String.format(Locale.US, "%.1f", x)).append(' ').append(y)
                .append(" Td (").append(pdf(text)).append(") Tj ET\n");
    }

    private void drawKpi(StringBuilder c, float x, float y, String label, String value) {
        c.append("0.42 0.42 0.42 rg BT /F1 7 Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(pdf(label)).append(") Tj ET\n");
        c.append("0.122 0.227 0.204 rg BT /F2 11 Tf ").append(x).append(' ').append(y - 16)
                .append(" Td (").append(pdf(clip(value, 16))).append(") Tj ET\n");
    }

    private float drawSectionTitle(StringBuilder c, float x, float y, String title) {
        c.append("0.122 0.227 0.204 rg BT /F2 11 Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(pdf(title)).append(") Tj ET\n");
        c.append("0.784 0.663 0.494 RG ").append(x).append(' ').append(y - 4).append(" m ")
                .append(x + 220).append(' ').append(y - 4).append(" l S\n");
        return y - 18;
    }

    private float drawKeyValueRow(StringBuilder c, float y, String label, String value) {
        c.append("0.85 0.85 0.85 RG 40 ").append(y - 4).append(" m 555 ").append(y - 4).append(" l S\n");
        c.append("0.42 0.42 0.42 rg BT /F1 9 Tf 44 ").append(y + 2)
                .append(" Td (").append(pdf(label)).append(") Tj ET\n");
        c.append("0 0 0 rg BT /F2 9 Tf 420 ").append(y + 2)
                .append(" Td (").append(pdf(value)).append(") Tj ET\n");
        return y - 18;
    }

    private float drawWrapped(StringBuilder c, String text, float x, float startY, float maxWidth,
                              float fontSize, float leading) {
        List<String> lines = wrapText(text, (int) (maxWidth / (fontSize * 0.5f)));
        float y = startY;
        for (String line : lines) {
            c.append("BT /F1 ").append(fontSize).append(" Tf ").append(x).append(' ').append(y)
                    .append(" Td (").append(pdf(line)).append(") Tj ET\n");
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
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private void appendFooter(StringBuilder c, int pageNum, int totalPages) {
        c.append("0.7 0.7 0.7 RG 40 40 m 555 40 l S\n");
        c.append("0.45 0.45 0.45 rg BT /F1 7 Tf 40 28 Td (JCT Contracting  |  Profit & Loss Report  |  Page ")
                .append(pageNum).append(" of ").append(totalPages).append(") Tj ET\n");
    }

    private byte[] assembleMultiPagePdf(List<String> pageContents) {
        try {
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            List<Long> xrefPositions = new ArrayList<>();
            writeString(pdfOut, "%PDF-1.4\n");

            int nextObj = 1;
            int catalogObjId = nextObj++;
            int pagesObjId = nextObj++;
            int fontRegularId = nextObj++;
            int fontBoldId = nextObj++;

            int pageCount = pageContents.size();
            int[] pageObjIds = new int[pageCount];
            int[] contentObjIds = new int[pageCount];
            for (int i = 0; i < pageCount; i++) {
                pageObjIds[i] = nextObj++;
                contentObjIds[i] = nextObj++;
            }

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, catalogObjId + " 0 obj\n<< /Type /Catalog /Pages " + pagesObjId + " 0 R >>\nendobj\n");

            StringBuilder kids = new StringBuilder("[");
            for (int id : pageObjIds) {
                kids.append(id).append(" 0 R ");
            }
            kids.append("]");
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pagesObjId + " 0 obj\n<< /Type /Pages /Count " + pageCount
                    + " /Kids " + kids + " >>\nendobj\n");

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontRegularId
                    + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontBoldId
                    + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            String resources = "<< /Font << /F1 " + fontRegularId + " 0 R /F2 " + fontBoldId + " 0 R >> >>";

            for (int i = 0; i < pageCount; i++) {
                xrefPositions.add((long) pdfOut.size());
                writeString(pdfOut, pageObjIds[i] + " 0 obj\n<< /Type /Page /Parent " + pagesObjId
                        + " 0 R /MediaBox [0 0 595 842] /Resources " + resources
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
            log.error("Failed to assemble P&L PDF", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Sanitize to WinAnsi-safe text and escape PDF string delimiters. */
    private static String pdf(String value) {
        if (value == null) {
            return "";
        }
        String s = value
                .replace('\u2014', '-')
                .replace('\u2013', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace("\u2026", "...")
                .replace('\u00A0', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else if ((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 255)) {
                sb.append(ch);
            }
        }
        return sb.toString()
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String clip(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)) + ".";
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String num(BigDecimal value) {
        return value != null ? value.toPlainString() : "";
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return MONEY.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static String nullSafe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
