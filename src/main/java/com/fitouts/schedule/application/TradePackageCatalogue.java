package com.fitouts.schedule.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fitouts.schedule.domain.TradePackage;
import com.fitouts.schedule.domain.TradePackageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns the trade label a template activity carries in prose ("Aluminium and glazing",
 * "Pool specialist") into a subcontract package code.
 *
 * <p>The seed does not link the two: activities name a trade in free text and the package
 * list is a separate sheet. Matching is by authored keywords held on the catalogue row, taken
 * in catalogue order and stopping at the first hit. Order matters, and the seed's own order
 * happens to be the right one: specific trades come before the catch-alls, so "Pool
 * specialist" resolves to the swimming pool package rather than to specialist finishes.
 *
 * <p>Labels that name in-house or consultant work — main contractor, design, PRO,
 * procurement, HSE — deliberately resolve to nothing. They are not subcontract packages and
 * should not produce package shells.
 */
@Component
@RequiredArgsConstructor
public class TradePackageCatalogue {

    /**
     * Keywords per package code. Authored rather than derived: package names alone do not
     * contain the words the templates actually use ("Gypsum" for "Gypsum and false ceiling",
     * "Glass and mirrors" for the aluminium package).
     */
    private static final Map<String, String> SEED_KEYWORDS = new LinkedHashMap<>();

    static {
        SEED_KEYWORDS.put("PKG-DEM", "demolition,strip out,waste removal");
        SEED_KEYWORDS.put("PKG-CIV", "civil,blockwork,plaster,screed,masonry");
        SEED_KEYWORDS.put("PKG-WPF", "waterproofing,tanking,membrane");
        SEED_KEYWORDS.put("PKG-TIL", "tiling,tile,flooring,skirting");
        SEED_KEYWORDS.put("PKG-MAR", "marble,quartz,solid surface,stone,countertop");
        SEED_KEYWORDS.put("PKG-GYP", "gypsum,false ceiling,ceiling,drywall");
        SEED_KEYWORDS.put("PKG-PNT", "painting,paint,decorative");
        SEED_KEYWORDS.put("PKG-JOI", "joinery,carpentry,wardrobe");
        SEED_KEYWORDS.put("PKG-ALU", "aluminium,glazing,glass,mirror,facade");
        SEED_KEYWORDS.put("PKG-DOR", "doors,ironmongery");
        SEED_KEYWORDS.put("PKG-ELE", "electrical,low current,lighting");
        SEED_KEYWORDS.put("PKG-PLB", "plumbing,drainage,sanitary");
        SEED_KEYWORDS.put("PKG-HVA", "hvac,air conditioning,ducting");
        SEED_KEYWORDS.put("PKG-FIR", "fire alarm,fire fighting,fire");
        SEED_KEYWORDS.put("PKG-SEC", "cctv,access control,security");
        SEED_KEYWORDS.put("PKG-SMT", "smart home,audio visual");
        SEED_KEYWORDS.put("PKG-POO", "swimming pool,pool");
        SEED_KEYWORDS.put("PKG-LAN", "landscape,irrigation,softscape,hardscape");
        SEED_KEYWORDS.put("PKG-SCF", "scaffolding,access platform");
        SEED_KEYWORDS.put("PKG-CLN", "cleaning");
        SEED_KEYWORDS.put("PKG-SPC", "specialist");
        SEED_KEYWORDS.put("PKG-MEP", "mep,mechanical electrical");
    }

    /**
     * The templates label a third of their MEP work simply "MEP", which the seed's package
     * sheet has no row for. Without this the twelve combined-services activities produce no
     * package shell at all, so the row is created on import and marked as added by us.
     */
    static final String DERIVED_MEP_CODE = "PKG-MEP";
    static final String DERIVED_MEP_NAME = "MEP combined (electrical, plumbing, HVAC)";

    private final TradePackageRepository repository;

    /** Keywords for a package code, or null when the code is not one the seed defines. */
    public static String seedKeywords(String code) {
        return SEED_KEYWORDS.get(code == null ? null : code.toUpperCase(Locale.ROOT));
    }

    /** A snapshot of the global catalogue, ready for repeated lookups during an import. */
    public Lookup lookup() {
        return new Lookup(repository.findByCompanyIdIsNullOrderBySortOrderAsc());
    }

    /** An immutable label-to-code resolver over one catalogue snapshot. */
    public static class Lookup {

        private final List<TradePackage> packages;

        Lookup(List<TradePackage> packages) {
            this.packages = packages;
        }

        /** The package whose keywords first match this trade label, or null. */
        public String codeForLabel(String label) {
            TradePackage match = packageForLabel(label);
            return match == null ? null : match.getCode();
        }

        public TradePackage packageForLabel(String label) {
            if (label == null || label.isBlank()) return null;
            String haystack = label.toLowerCase(Locale.ROOT);
            for (TradePackage candidate : packages) {
                for (String keyword : keywordsOf(candidate)) {
                    if (haystack.contains(keyword)) return candidate;
                }
            }
            return null;
        }

        public TradePackage byCode(String code) {
            if (code == null) return null;
            return packages.stream().filter(p -> p.getCode().equalsIgnoreCase(code)).findFirst().orElse(null);
        }

        private List<String> keywordsOf(TradePackage pkg) {
            List<String> out = new ArrayList<>();
            String raw = pkg.getMatchKeywords() != null ? pkg.getMatchKeywords() : pkg.getName();
            for (String part : raw.toLowerCase(Locale.ROOT).split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            return out;
        }
    }
}
