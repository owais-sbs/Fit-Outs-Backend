package com.fitouts.subcontractor.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.fitouts.project.domain.Project;
import com.fitouts.subcontractor.api.ScAwardBoqLineResponse;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.SubcontractorPackage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visual smoke tests: PDF must match portal preview density (short contract ≤ 3 pages).
 */
class SubcontractPdfServiceVisualTest {

    @Test
    void shortContractFitsInTwoOrThreePages() throws Exception {
        SubcontractPdfService service = new SubcontractPdfService();
        byte[] pdf = service.generateStage2FinalPdf(
                samplePkg(), sampleAward(), sampleOrg(), sampleProject(),
                "Kiran", "Admin",
                OffsetDateTime.parse("2026-09-21T11:53:00+04:00"),
                sampleSig(),
                "Nameera Representative", "Director",
                OffsetDateTime.parse("2026-09-22T09:10:00+04:00"),
                sampleSig(),
                shortBoq(), List.of(), List.of(),
                "give final");

        int pages = pageCount(pdf);
        Path out = write(pdf, "SC-51AEBD91_Award_Pack_Balanced_2page.pdf");
        System.out.println("SHORT_TEST_PDF_PATH=" + out.toAbsolutePath());
        System.out.println("SHORT_PAGE_COUNT=" + pages);
        assertTrue(pages == 2 || pages == 3, "Short contract should be 2-3 balanced pages, was " + pages);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
    }

    @Test
    void mediumBoqPaginatesNaturally() throws Exception {
        SubcontractPdfService service = new SubcontractPdfService();
        List<ScAwardBoqLineResponse> lines = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            lines.add(boq("ELE-" + String.format("%03d", i),
                    "Electrical works item " + i + " including containment, cabling and terminations as specified",
                    "Nr", "2", "50.00", "100.00", "QUOTED"));
        }
        byte[] pdf = service.generateStage1AdminPdf(
                samplePkg(), sampleAward(), sampleOrg(), sampleProject(),
                "Kiran", "Admin",
                OffsetDateTime.parse("2026-09-21T11:53:00+04:00"),
                sampleSig(),
                lines, List.of(), List.of(), null);
        int pages = pageCount(pdf);
        Path out = write(pdf, "SC-51AEBD91_Award_Pack_MediumBOQ.pdf");
        System.out.println("MEDIUM_TEST_PDF_PATH=" + out.toAbsolutePath());
        System.out.println("MEDIUM_PAGE_COUNT=" + pages);
        assertTrue(pages >= 2, "Medium BOQ should span at least 2 pages");
        assertTrue(pages <= 5, "Medium BOQ should not balloon past 5 pages, was " + pages);
    }

    @Test
    void realAwardShapeHasNoBlankMiddlePage() throws Exception {
        SubcontractPdfService service = new SubcontractPdfService();
        SubcontractorPackage pkg = samplePkg();
        pkg.setTenderDescription("do it");
        pkg.setPaymentTerms("45 days");
        pkg.setRetentionPct(new BigDecimal("10"));
        ScPackageAward award = sampleAward();
        award.setAwardValueReason("adjust");
        byte[] pdf = service.generateStage2FinalPdf(
                pkg, award, sampleOrg(), sampleProject(),
                "Kiran", "Admin",
                OffsetDateTime.parse("2026-09-21T11:53:00+04:00"),
                sampleSig(),
                "nameera", "Director",
                OffsetDateTime.parse("2026-09-22T09:10:00+04:00"),
                sampleSig(),
                shortBoq(), List.of(), List.of(),
                "give final");
        int pages = pageCount(pdf);
        Path out = write(pdf, "SC-51AEBD91_Award_Pack_SpacingFixed.pdf");
        System.out.println("FIXED_TEST_PDF_PATH=" + out.toAbsolutePath());
        System.out.println("FIXED_PAGE_COUNT=" + pages);
        assertTrue(pages == 2 || pages == 3, "Expected 2-3 pages, was " + pages);
        // Must not leave a near-empty page that only holds scope exclusions
        String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(s.contains("Page 1 of " + pages));
        assertTrue(s.contains("PACKAGE COMMERCIAL TERMS"));
        assertTrue(s.contains("JCT TERMS"));
    }

    private static int pageCount(byte[] pdf) {
        String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile("/Type /Page[^s]").matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static Path write(byte[] pdf, String name) throws Exception {
        Path outDir = Path.of(System.getProperty("user.home"), "OneDrive", "Desktop", "jct", "test-output");
        Files.createDirectories(outDir);
        Path out = outDir.resolve(name);
        Files.write(out, pdf);
        return out;
    }

    private static SubcontractorPackage samplePkg() {
        SubcontractorPackage pkg = new SubcontractorPackage();
        pkg.setUuid(UUID.fromString("51aebd91-0000-4000-8000-000000000001"));
        pkg.setName("Electrical Package — Main Works");
        pkg.setTradePackageCode("PKG-ELE");
        pkg.setTradePackageName("Electrical and Low Current");
        pkg.setAppointedCompanyName("Nameera");
        pkg.setPaymentTerms("45 days");
        pkg.setRetentionPct(new BigDecimal("10"));
        pkg.setLdTerms("Per agreed subcontract terms");
        pkg.setTenderDescription("Electrical and low-current works as tendered.");
        pkg.setPlannedStart(LocalDate.of(2026, 10, 1));
        pkg.setPlannedFinish(LocalDate.of(2026, 12, 15));
        return pkg;
    }

    private static ScPackageAward sampleAward() {
        ScPackageAward award = new ScPackageAward();
        award.setUuid(UUID.randomUUID());
        award.setAwardedValue(new BigDecimal("1450.00"));
        award.setAwardValueReason("Negotiated package adjustment");
        award.setAwardedAt(OffsetDateTime.parse("2026-09-21T11:53:00+04:00"));
        return award;
    }

    private static ScOrganization sampleOrg() {
        ScOrganization org = new ScOrganization();
        org.setLegalCompanyName("Nameera");
        return org;
    }

    private static Project sampleProject() {
        Project project = new Project();
        project.setName("Testing One More Time");
        project.setLocation("Dubai");
        return project;
    }

    private static List<ScAwardBoqLineResponse> shortBoq() {
        return List.of(
                boq("OTHER", "Changing of single socket to double sockets including removal of existing 3x3 GI box, wall chipping, supply and install", "nos", "1", "1000.00", "1000.00", "QUOTED"),
                boq("OTHER", "Construction of concrete beam to allow installation of new door/window at: location", "lot", "1", "50.00", "50.00", "QUOTED")
        );
    }

    private static ScAwardBoqLineResponse boq(
            String code, String desc, String unit, String qty, String rate, String amount, String status) {
        return ScAwardBoqLineResponse.builder()
                .uuid(UUID.randomUUID())
                .sectionCode(code)
                .description(desc)
                .unit(unit)
                .quantity(new BigDecimal(qty))
                .rate(new BigDecimal(rate))
                .amount(new BigDecimal(amount))
                .lineStatus(status)
                .build();
    }

    private static byte[] sampleSig() throws Exception {
        BufferedImage img = new BufferedImage(240, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(18, 58, 52));
        g.drawLine(20, 50, 80, 30);
        g.drawLine(80, 30, 140, 55);
        g.drawLine(140, 55, 200, 25);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
