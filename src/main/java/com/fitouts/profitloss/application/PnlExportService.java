package com.fitouts.profitloss.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fitouts.profitloss.api.CompanyPnlResponse;
import com.fitouts.profitloss.api.ProjectPnlResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnlExportService {

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
        return renderPdf("Company P&L " + pnl.getPeriodYearMonth(), pnl);
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
        return renderPdf("Project P&L - " + nullSafe(row.getProjectName()), wrap);
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

    private byte[] renderPdf(String title, CompanyPnlResponse pnl) {
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 16 Tf 40 800 Td (").append(escapePdf(title)).append(") Tj ET\n");
        content.append("BT /F1 10 Tf 40 780 Td (Contract: ").append(escapePdf(num(pnl.getContractValue())))
                .append("  Total cost: ").append(escapePdf(num(pnl.getTotalCost())))
                .append("  Margin: ").append(escapePdf(num(pnl.getMargin())))
                .append(") Tj ET\n");
        content.append("BT /F1 9 Tf 40 760 Td (Labour cost is not tracked in v1 and is shown as 0.) Tj ET\n");

        int y = 730;
        content.append("BT /F1 9 Tf 40 ").append(y).append(" Td (Project | Contract | Materials | SC | Variation | Overhead | Total | Margin) Tj ET\n");
        y -= 16;
        for (ProjectPnlResponse row : pnl.getProjects()) {
            if (y < 60) {
                break;
            }
            String line = truncate(nullSafe(row.getProjectName()), 24)
                    + " | " + num(row.getContractValue())
                    + " | " + num(row.getMaterialCost())
                    + " | " + num(row.getScCertifiedCost())
                    + " | " + num(row.getVariationCost())
                    + " | " + num(row.getOverheadAllocated())
                    + " | " + num(row.getTotalCost())
                    + " | " + num(row.getMargin());
            content.append("BT /F1 8 Tf 40 ").append(y).append(" Td (").append(escapePdf(line)).append(") Tj ET\n");
            y -= 14;
        }
        return assemblePdf(content.toString());
    }

    private byte[] assemblePdf(String contentStream) {
        try {
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            List<Long> xrefPositions = new ArrayList<>();
            writeString(pdfOut, "%PDF-1.4\n");

            int catalogObjId = 1;
            int pagesObjId = 2;
            int pageObjId = 3;
            int fontObjId = 4;
            int contentObjId = 5;

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, catalogObjId + " 0 obj\n<< /Type /Catalog /Pages " + pagesObjId + " 0 R >>\nendobj\n");

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pagesObjId + " 0 obj\n<< /Type /Pages /Count 1 /Kids [" + pageObjId + " 0 R] >>\nendobj\n");

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, pageObjId + " 0 obj\n<< /Type /Page /Parent " + pagesObjId
                    + " 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 " + fontObjId
                    + " 0 R >> >> /Contents " + contentObjId + " 0 R >>\nendobj\n");

            xrefPositions.add((long) pdfOut.size());
            writeString(pdfOut, fontObjId + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            xrefPositions.add((long) pdfOut.size());
            byte[] contentBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);
            writeString(pdfOut, contentObjId + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            pdfOut.write(contentBytes);
            writeString(pdfOut, "\nendstream\nendobj\n");

            long startXref = pdfOut.size();
            writeString(pdfOut, "xref\n0 " + (xrefPositions.size() + 1) + "\n");
            writeString(pdfOut, "0000000000 65535 f \n");
            for (Long pos : xrefPositions) {
                writeString(pdfOut, String.format("%010d 00000 n \n", pos));
            }
            writeString(pdfOut, "trailer\n<< /Size " + (xrefPositions.size() + 1) + " /Root " + catalogObjId + " 0 R >>\n");
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

    private static String escapePdf(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + ".";
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

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
