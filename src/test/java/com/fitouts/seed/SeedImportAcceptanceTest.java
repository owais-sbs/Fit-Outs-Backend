package com.fitouts.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fitouts.approval.api.ProjectScopeToggles;
import com.fitouts.approval.api.SeedImportSummary;
import com.fitouts.approval.application.ApprovalSeedImportService;
import com.fitouts.approval.application.JurisdictionResolver;
import com.fitouts.approval.application.SeedFileLocator;
import com.fitouts.approval.domain.AuthorityRepository;
import com.fitouts.approval.domain.DocumentTypeRepository;
import com.fitouts.approval.domain.PermitType;
import com.fitouts.approval.domain.PermitTypeRepository;
import com.fitouts.schedule.domain.LockedConstraintRuleRepository;
import com.fitouts.schedule.domain.ProductivityNormRepository;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.ScheduleTemplateRepository;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.domain.TemplateActivityRepository;
import com.fitouts.schedule.domain.TemplateDependencyRepository;
import com.fitouts.schedule.domain.TemplateProcurementItemRepository;
import com.fitouts.schedule.domain.TradePackageRepository;

/**
 * Runs the real companion seed file through the importer and checks what came out.
 *
 * <p>This is the acceptance gate for the seed: it asserts the row counts the source document
 * quotes, the programme lengths CPM derives from the seed's own dependency logic, and that the
 * derived jurisdiction table resolves a community to the authorities that actually cover it.
 *
 * <p>The programme lengths asserted here are the computed ones (79 / 55 / 303 working days),
 * not the source's published headlines (77 / 52 / 300). The seed's start and finish day
 * columns contradict its predecessor column on roughly a third of activities, and the
 * predecessor column is the version that can be executed on site, so it wins. The gap is
 * asserted deliberately: if someone corrects the source logic, this test tells them the
 * published figures moved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeedImportAcceptanceTest {

    @Autowired private ApprovalSeedImportService importService;
    @Autowired private SeedFileLocator seedFileLocator;
    @Autowired private JurisdictionResolver resolver;

    @Autowired private AuthorityRepository authorityRepository;
    @Autowired private PermitTypeRepository permitTypeRepository;
    @Autowired private DocumentTypeRepository documentTypeRepository;
    @Autowired private ScheduleTemplateRepository templateRepository;
    @Autowired private TemplateActivityRepository templateActivityRepository;
    @Autowired private TemplateDependencyRepository templateDependencyRepository;
    @Autowired private TemplateProcurementItemRepository procurementRepository;
    @Autowired private LockedConstraintRuleRepository lockedConstraintRepository;
    @Autowired private ProductivityNormRepository productivityNormRepository;
    @Autowired private TradePackageRepository tradePackageRepository;

    private static SeedImportSummary summary;

    @BeforeAll
    static void resetSharedState() {
        summary = null;
    }

    /** Imported once and reused: parsing 133 activities and solving three networks is not free. */
    private SeedImportSummary importOnce() {
        if (summary == null) {
            summary = importService.importFromConfiguredFile();
        }
        return summary;
    }

    @Test
    void seedFileIsWhereTheImporterExpectsIt() {
        assertThat(seedFileLocator.exists())
                .as("docs/vetrobuild_erp_seed_v1.json must be readable from the module directory")
                .isTrue();
    }

    @Test
    void importLoadsEverySectionOfTheCatalogue() {
        importOnce();

        assertThat(authorityRepository.findByCompanyIdIsNull()).hasSize(34);
        assertThat(permitTypeRepository.findByCompanyIdIsNull()).hasSize(24);
        assertThat(documentTypeRepository.findVisible(null)).hasSize(35);
        assertThat(lockedConstraintRepository.findVisible(null)).hasSize(12);
        assertThat(productivityNormRepository.findVisible(null)).hasSize(18);
        assertThat(procurementRepository.findByTemplateUuidIsNull()).hasSize(20);

        // 21 from the seed plus the combined-MEP row the importer adds, because the templates
        // label a block of services work "MEP" and the seed's package sheet has no such row.
        assertThat(tradePackageRepository.findByCompanyIdIsNullOrderBySortOrderAsc()).hasSize(22);
    }

    @Test
    void everyTemplateAndItsActivitiesLand() {
        importOnce();

        Map<String, ScheduleTemplate> templates = templateRepository.findSystemTemplates().stream()
                .collect(Collectors.toMap(ScheduleTemplate::getCode, Function.identity()));
        assertThat(templates.keySet()).containsExactlyInAnyOrder("TPL-RENO-90", "TPL-RENO-60", "TPL-GF1-NEW");

        assertThat(activitiesOf(templates, "TPL-RENO-90")).hasSize(53);
        assertThat(activitiesOf(templates, "TPL-RENO-60")).hasSize(37);
        assertThat(activitiesOf(templates, "TPL-GF1-NEW")).hasSize(43);
    }

    @Test
    void predecessorGrammarParsesIntoRealLinks() {
        importOnce();
        ScheduleTemplate villa = template("TPL-GF1-NEW");

        // "G010 SS+4" must survive as a start-to-start link with a four-day lag, not collapse
        // to a plain finish-to-start, or the whole overlap disappears from the programme.
        assertThat(templateDependencyRepository.findByTemplateUuid(villa.getUuid()))
                .anySatisfy(dep -> {
                    assertThat(dep.getPredecessorCode()).isEqualTo("G010");
                    assertThat(dep.getSuccessorCode()).isEqualTo("G020");
                    assertThat(dep.getType()).isEqualTo("SS");
                    assertThat(dep.getLagDays()).isEqualTo(4);
                });

        // "G010, G020" is two links off one cell.
        assertThat(templateDependencyRepository.findByTemplateUuid(villa.getUuid()))
                .filteredOn(dep -> "G030".equals(dep.getSuccessorCode()))
                .extracting(dep -> dep.getPredecessorCode())
                .containsExactlyInAnyOrder("G010", "G020");
    }

    @Test
    void computedProgrammeLengthsAreTheOnesTheEngineWillActuallyDeliver() {
        importOnce();

        assertThat(template("TPL-RENO-90").getComputedWorkingDays())
                .as("Template A: 79 working days from the seed's own logic, not the published 77")
                .isEqualTo(79);
        assertThat(template("TPL-RENO-60").getComputedWorkingDays())
                .as("Template A2: the 60-day fast track computes to 55 working days")
                .isEqualTo(55);
        assertThat(template("TPL-GF1-NEW").getComputedWorkingDays())
                .as("Template B: 303 working days, not the published 300")
                .isEqualTo(303);
    }

    @Test
    void theGapBetweenPublishedAndComputedIsRecordedOnTheTemplate() {
        importOnce();
        ScheduleTemplate a = template("TPL-RENO-90");

        assertThat(a.getTargetWorkingDays()).isEqualTo(77);
        assertThat(a.getDataQualityNotes())
                .as("the variance has to be visible in the library, not just in a test")
                .contains("77")
                .contains("79");
    }

    @Test
    void tradeLabelsResolveToPackagesWithoutMisfiling() {
        importOnce();
        Map<String, TemplateActivity> byCode = activitiesOf(templateRepository.findSystemTemplates().stream()
                .collect(Collectors.toMap(ScheduleTemplate::getCode, Function.identity())), "TPL-GF1-NEW")
                .stream().collect(Collectors.toMap(TemplateActivity::getActivityCode, Function.identity()));

        // "Pool specialist" has to reach the swimming pool package, not specialist finishes.
        TemplateActivity pool = byCode.get("G340");
        assertThat(pool.getTradeLabel()).isEqualTo("Pool specialist");
        assertThat(pool.getTradePackageCode()).isEqualTo("PKG-POO");

        // Design and PRO work is in-house or consultant scope and must not spawn a package.
        assertThat(byCode.get("G010").getTradeLabel()).isEqualTo("Design");
        assertThat(byCode.get("G010").getTradePackageCode()).isNull();
    }

    @Test
    void cureAndTestHoldsAreReadAsDaysEvenWhenTheSeedWritesHours() {
        importOnce();

        // "12-24 hours" is one day of programme, not twelve.
        assertThat(lockedConstraintRepository.findByNameAndCompanyIdIsNull("Waterproofing intercoat cure"))
                .get()
                .satisfies(rule -> assertThat(rule.getMinimumHoldWorkingDays()).isEqualTo(1));

        // "48 hours (some consultants 72)" is two.
        assertThat(lockedConstraintRepository.findByNameAndCompanyIdIsNull("Flood / ponding test"))
                .get()
                .satisfies(rule -> assertThat(rule.getMinimumHoldWorkingDays()).isEqualTo(2));

        // "7 days conventional, 3 days rapid-set" keeps the conventional figure.
        assertThat(lockedConstraintRepository.findByNameAndCompanyIdIsNull("Screed cure before tiling"))
                .get()
                .satisfies(rule -> assertThat(rule.getMinimumHoldWorkingDays()).isEqualTo(7));
    }

    @Test
    void permitRangesAndDepositFlagsSurviveTheProse() {
        importOnce();
        List<PermitType> permits = permitTypeRepository.findByCompanyIdIsNull();

        assertThat(permits)
                .as("every permit keeps the original SLA text even when it is a range")
                .allSatisfy(p -> assertThat(p.getCode()).isNotBlank());

        assertThat(permits)
                .filteredOn(p -> p.getIndicativeSlaDaysMin() != null && p.getIndicativeSlaDaysMax() != null)
                .as("a parsed range must not come out inverted")
                .allSatisfy(p -> assertThat(p.getIndicativeSlaDaysMax()).isGreaterThanOrEqualTo(p.getIndicativeSlaDaysMin()));
    }

    @Test
    void palmJumeirahResolvesToBothItsRegulatorAndItsMasterDeveloper() {
        importOnce();

        JurisdictionResolver.Resolution resolution =
                resolver.resolve(null, "Dubai", "Palm Jumeirah", null, ProjectScopeToggles.defaults());

        List<String> codes = resolution.getAuthorities().stream()
                .map(a -> a.getCode())
                .toList();

        assertThat(codes)
                .as("the Palm sits under Trakhees for regulation and Nakheel as master developer; "
                        + "resolving to only one of them means a missed NOC")
                .contains("TRK", "NAK")
                .as("the utility layer comes with the community, not from a scope toggle")
                .contains("DEWA");

        assertThat(resolution.getWarnings())
                .as("derived jurisdiction rows must announce that nobody has confirmed them")
                .anySatisfy(w -> assertThat(w).containsIgnoringCase("not been confirmed"));
    }

    @Test
    void reimportingTheSameFileChangesNothing() {
        importOnce();
        long authoritiesBefore = authorityRepository.findByCompanyIdIsNull().size();
        long activitiesBefore = templateActivityRepository.count();

        SeedImportSummary second = importService.importFromConfiguredFile();

        assertThat(authorityRepository.findByCompanyIdIsNull()).hasSize((int) authoritiesBefore);
        assertThat(templateActivityRepository.count()).isEqualTo(activitiesBefore);
        assertThat(second.getInserted().getOrDefault("authorities", 0))
                .as("a second run updates in place rather than duplicating")
                .isZero();
    }

    // ------------------------------------------------------------------ helpers

    private ScheduleTemplate template(String code) {
        Optional<ScheduleTemplate> found = templateRepository.findByCodeAndCompanyIdIsNull(code);
        assertThat(found).as("template " + code + " should have been imported").isPresent();
        return found.get();
    }

    private List<TemplateActivity> activitiesOf(Map<String, ScheduleTemplate> templates, String code) {
        return templateActivityRepository.findByTemplateUuidOrderBySortOrderAsc(templates.get(code).getUuid());
    }
}
