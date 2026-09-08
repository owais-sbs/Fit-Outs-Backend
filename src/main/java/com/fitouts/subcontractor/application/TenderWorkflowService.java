package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.subcontractor.api.AwardPackResponse;
import com.fitouts.subcontractor.api.NormalizedComparisonResponse;
import com.fitouts.subcontractor.api.PackageAddendumRequest;
import com.fitouts.subcontractor.api.PackageAddendumResponse;
import com.fitouts.subcontractor.api.PackageAwardRequest;
import com.fitouts.subcontractor.api.PackageAwardResponse;
import com.fitouts.subcontractor.api.PackageBidderResponse;
import com.fitouts.subcontractor.api.PackageClarificationRequest;
import com.fitouts.subcontractor.api.PackageClarificationResponse;
import com.fitouts.subcontractor.api.QuoteLineRequest;
import com.fitouts.subcontractor.api.QuoteLineResponse;
import com.fitouts.subcontractor.api.QuoteRequest;
import com.fitouts.subcontractor.api.QuoteResponse;
import com.fitouts.subcontractor.api.TenderPackageRequest;
import com.fitouts.subcontractor.api.TenderPackageResponse;
import com.fitouts.subcontractor.domain.Package;
import com.fitouts.subcontractor.domain.PackageAddendum;
import com.fitouts.subcontractor.domain.PackageAddendumAck;
import com.fitouts.subcontractor.domain.PackageAddendumAckRepository;
import com.fitouts.subcontractor.domain.PackageAddendumRepository;
import com.fitouts.subcontractor.domain.PackageAward;
import com.fitouts.subcontractor.domain.PackageAwardRepository;
import com.fitouts.subcontractor.domain.PackageBidder;
import com.fitouts.subcontractor.domain.PackageBidderRepository;
import com.fitouts.subcontractor.domain.PackageClarification;
import com.fitouts.subcontractor.domain.PackageClarificationRepository;
import com.fitouts.subcontractor.domain.PackageRepository;
import com.fitouts.subcontractor.domain.PackageStatus;
import com.fitouts.subcontractor.domain.Quote;
import com.fitouts.subcontractor.domain.QuoteLine;
import com.fitouts.subcontractor.domain.QuoteLineRepository;
import com.fitouts.subcontractor.domain.QuoteRepository;
import com.fitouts.subcontractor.domain.Subcontractor;
import com.fitouts.subcontractor.domain.SubcontractorDocument;
import com.fitouts.subcontractor.domain.SubcontractorDocumentRepository;
import com.fitouts.subcontractor.domain.SubcontractorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TenderWorkflowService {

    private final PackageRepository packageRepository;
    private final PackageBidderRepository packageBidderRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteLineRepository quoteLineRepository;
    private final PackageClarificationRepository packageClarificationRepository;
    private final PackageAddendumRepository packageAddendumRepository;
    private final PackageAddendumAckRepository packageAddendumAckRepository;
    private final PackageAwardRepository packageAwardRepository;
    private final SubcontractorRepository subcontractorRepository;
    private final SubcontractorDocumentRepository subcontractorDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final BoqDocumentRepository boqDocumentRepository;

    public TenderPackageResponse createPackage(Long projectId, TenderPackageRequest request, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new IllegalStateException("CompanyContext must be set");
        }

        Package pkg = new Package();
        pkg.setProjectId(projectId);
        pkg.setCompanyId(companyId);
        pkg.setPackageRef(request.getPackageRef());
        pkg.setTitle(request.getTitle());
        pkg.setScope(request.getScope());

        if (request.getBoqDocumentId() != null) {
            boqDocumentRepository.findById(request.getBoqDocumentId())
                    .orElseThrow(() -> new BadRequestException("BOQ Document not found: " + request.getBoqDocumentId()));
            pkg.setBoqDocumentId(request.getBoqDocumentId());
        }

        if (request.getBoqLineIds() != null && !request.getBoqLineIds().isEmpty()) {
            if (request.getBoqDocumentId() == null) {
                throw new BadRequestException("boqDocumentId is required when boqLineIds are provided");
            }
            List<UUID> validLineIds = new ArrayList<>();
            for (UUID lineId : request.getBoqLineIds()) {
                BoqLine line = boqLineRepository.findById(lineId)
                        .orElseThrow(() -> new BadRequestException("BOQ Line not found: " + lineId));
                if (line.getBoq() == null || !request.getBoqDocumentId().equals(line.getBoq().getId())) {
                    throw new BadRequestException("BOQ Line " + lineId + " does not belong to BOQ Document " + request.getBoqDocumentId());
                }
                validLineIds.add(lineId);
            }
            pkg.setBoqLineIds(validLineIds);
        }

        pkg.setTrade(request.getTrade());
        pkg.setDrawingRevision(request.getDrawingRevision());
        pkg.setSpecificationSummary(request.getSpecificationSummary());
        pkg.setSiteVisitInfo(request.getSiteVisitInfo());
        pkg.setTenderDeadline(request.getTenderDeadline());
        pkg.setStatus(PackageStatus.DRAFT);
        pkg.setBidsOpened(false);

        if (request.getRequireCarInsurance() != null) pkg.setRequireCarInsurance(request.getRequireCarInsurance());
        if (request.getRequireTplInsurance() != null) pkg.setRequireTplInsurance(request.getRequireTplInsurance());
        if (request.getRequireWorkmenComp() != null) pkg.setRequireWorkmenComp(request.getRequireWorkmenComp());
        if (request.getRequireCivilDefence() != null) pkg.setRequireCivilDefence(request.getRequireCivilDefence());
        if (request.getRequireSiraLicence() != null) pkg.setRequireSiraLicence(request.getRequireSiraLicence());
        if (request.getRequireElectricalCert() != null) pkg.setRequireElectricalCert(request.getRequireElectricalCert());
        pkg.setRequiredCommunityReg(request.getRequiredCommunityReg());

        Package saved = packageRepository.save(pkg);
        return mapToPackageResponse(saved);
    }

    public List<TenderPackageResponse> getProjectPackages(Long projectId) {
        UUID companyId = CompanyContext.get();
        return packageRepository.findByCompanyIdAndProjectId(companyId, projectId)
                .stream()
                .map(this::mapToPackageResponse)
                .collect(Collectors.toList());
    }

    public TenderPackageResponse getPackageDetails(UUID packageId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }
        return mapToPackageResponse(pkg);
    }

    public PackageBidderResponse addBidder(UUID packageId, UUID subcontractorId, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        Subcontractor sc = subcontractorRepository.findById(subcontractorId)
                .orElseThrow(() -> new IllegalArgumentException("Subcontractor not found: " + subcontractorId));

        EligibilityResult eligibility = checkEligibility(pkg, sc);

        Optional<PackageBidder> existing = packageBidderRepository.findByPackageIdAndSubcontractorId(packageId, subcontractorId);
        PackageBidder bidder;
        if (existing.isPresent()) {
            bidder = existing.get();
        } else {
            bidder = new PackageBidder();
            bidder.setPackageId(packageId);
            bidder.setSubcontractorId(subcontractorId);
            bidder.setStatus("INVITED");
            bidder.setInvitedBy(currentAccountId);
            bidder.setInvitedAt(OffsetDateTime.now());
            bidder = packageBidderRepository.save(bidder);
        }

        PackageBidderResponse res = new PackageBidderResponse();
        res.setId(bidder.getId());
        res.setPackageId(packageId);
        res.setSubcontractorId(subcontractorId);
        res.setSubcontractorName(sc.getCompanyName());
        res.setStatus(bidder.getStatus());
        res.setInvitedAt(bidder.getInvitedAt());
        res.setAcknowledgedAt(bidder.getAcknowledgedAt());
        res.setIsEligible(eligibility.isEligible());
        res.setEligibilityReasons(eligibility.getReasons());
        return res;
    }

    public List<PackageBidderResponse> getBidders(UUID packageId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        return packageBidderRepository.findByPackageId(packageId).stream().map(bidder -> {
            Subcontractor sc = subcontractorRepository.findById(bidder.getSubcontractorId()).orElse(null);
            EligibilityResult eligibility = sc != null ? checkEligibility(pkg, sc) : new EligibilityResult(false, List.of("Subcontractor not found"));
            PackageBidderResponse res = new PackageBidderResponse();
            res.setId(bidder.getId());
            res.setPackageId(packageId);
            res.setSubcontractorId(bidder.getSubcontractorId());
            res.setSubcontractorName(sc != null ? sc.getCompanyName() : "Unknown");
            res.setStatus(bidder.getStatus());
            res.setInvitedAt(bidder.getInvitedAt());
            res.setAcknowledgedAt(bidder.getAcknowledgedAt());
            res.setIsEligible(eligibility.isEligible());
            res.setEligibilityReasons(eligibility.getReasons());
            return res;
        }).collect(Collectors.toList());
    }

    public TenderPackageResponse issuePackage(UUID packageId, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        pkg.setStatus(PackageStatus.ISSUED);
        Package saved = packageRepository.save(pkg);
        return mapToPackageResponse(saved);
    }

    public List<TenderPackageResponse> getScPackages(UUID subcontractorId) {
        List<PackageBidder> bids = packageBidderRepository.findBySubcontractorId(subcontractorId);
        List<TenderPackageResponse> list = new ArrayList<>();
        for (PackageBidder b : bids) {
            packageRepository.findById(b.getPackageId()).ifPresent(pkg -> list.add(mapToPackageResponse(pkg)));
        }
        return list;
    }

    public List<QuoteLineResponse> getScPackageBoqLines(UUID packageId, UUID subcontractorId) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        if (subcontractorId != null) {
            packageBidderRepository.findByPackageIdAndSubcontractorId(packageId, subcontractorId)
                    .orElseThrow(() -> new SecurityException("Subcontractor is not invited to this package"));
        }

        if (pkg.getBoqLineIds() == null || pkg.getBoqLineIds().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<QuoteLineResponse> responses = new ArrayList<>();
        for (UUID lineId : pkg.getBoqLineIds()) {
            boqLineRepository.findById(lineId).ifPresent(bl -> {
                QuoteLineResponse res = new QuoteLineResponse();
                res.setBoqLineId(bl.getId());
                res.setSectionCode(bl.getCategoryCode());
                res.setDescription(bl.getDescription());
                res.setQuantity(bl.getQuantity());
                res.setUnit(bl.getUnit());
                res.setUnitRate(bl.getRate());
                res.setAmount(bl.getAmount());
                responses.add(res);
            });
        }
        return responses;
    }

    public QuoteResponse submitQuote(UUID subcontractorId, QuoteRequest request, Long currentAccountId) {
        UUID packageId = request.getPackageId();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        // Ensure subcontractor is invited
        PackageBidder bidder = packageBidderRepository.findByPackageIdAndSubcontractorId(packageId, subcontractorId)
                .orElseThrow(() -> new IllegalArgumentException("Subcontractor is not invited to this package"));

        if (pkg.getBoqLineIds() != null && !pkg.getBoqLineIds().isEmpty() && request.getLines() != null) {
            for (QuoteLineRequest lr : request.getLines()) {
                if (lr.getBoqLineId() != null && !pkg.getBoqLineIds().contains(lr.getBoqLineId())) {
                    throw new BadRequestException("BOQ Line " + lr.getBoqLineId() + " is not part of this package's selected BOQ lines");
                }
            }
        }

        Quote quote = quoteRepository.findByPackageIdAndSubcontractorId(packageId, subcontractorId)
                .orElseGet(() -> {
                    Quote q = new Quote();
                    q.setPackageId(packageId);
                    q.setSubcontractorId(subcontractorId);
                    return q;
                });

        quote.setBidderAccountId(currentAccountId);
        quote.setStatus("SUBMITTED");
        quote.setRemarks(request.getRemarks());
        quote.setSubmittedAt(OffsetDateTime.now());
        quote = quoteRepository.save(quote);

        // Delete previous lines
        quoteLineRepository.deleteByQuoteId(quote.getId());

        BigDecimal total = BigDecimal.ZERO;
        List<QuoteLineResponse> lineResponses = new ArrayList<>();
        if (request.getLines() != null) {
            for (QuoteLineRequest lr : request.getLines()) {
                QuoteLine line = new QuoteLine();
                line.setQuoteId(quote.getId());
                line.setBoqLineId(lr.getBoqLineId());
                line.setSectionCode(lr.getSectionCode());
                line.setDescription(lr.getDescription());
                line.setQuantity(lr.getQuantity() != null ? lr.getQuantity() : BigDecimal.ZERO);
                line.setUnit(lr.getUnit());
                line.setUnitRate(lr.getUnitRate() != null ? lr.getUnitRate() : BigDecimal.ZERO);
                BigDecimal lineAmount = line.getQuantity().multiply(line.getUnitRate());
                line.setAmount(lineAmount);
                line.setRemarks(lr.getRemarks());

                QuoteLine savedLine = quoteLineRepository.save(line);
                total = total.add(lineAmount);

                QuoteLineResponse lineRes = new QuoteLineResponse();
                lineRes.setId(savedLine.getId());
                lineRes.setBoqLineId(savedLine.getBoqLineId());
                lineRes.setSectionCode(savedLine.getSectionCode());
                lineRes.setDescription(savedLine.getDescription());
                lineRes.setQuantity(savedLine.getQuantity());
                lineRes.setUnit(savedLine.getUnit());
                lineRes.setUnitRate(savedLine.getUnitRate());
                lineRes.setAmount(savedLine.getAmount());
                lineRes.setRemarks(savedLine.getRemarks());
                lineResponses.add(lineRes);
            }
        }

        quote.setTotalAmount(total);
        quote = quoteRepository.save(quote);

        bidder.setStatus("SUBMITTED");
        packageBidderRepository.save(bidder);

        Subcontractor sc = subcontractorRepository.findById(subcontractorId).orElse(null);

        QuoteResponse res = new QuoteResponse();
        res.setId(quote.getId());
        res.setPackageId(quote.getPackageId());
        res.setSubcontractorId(subcontractorId);
        res.setSubcontractorName(sc != null ? sc.getCompanyName() : "Unknown");
        res.setStatus(quote.getStatus());
        res.setTotalAmount(quote.getTotalAmount());
        res.setRemarks(quote.getRemarks());
        res.setSubmittedAt(quote.getSubmittedAt());
        res.setCreatedAt(quote.getCreatedAt());
        res.setLines(lineResponses);
        return res;
    }

    public QuoteResponse bulkUploadQuoteLines(UUID quoteId, UUID subcontractorId, List<QuoteLineRequest> lines, Long currentAccountId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Quote not found: " + quoteId));

        if (!quote.getSubcontractorId().equals(subcontractorId)) {
            throw new SecurityException("Quote does not belong to subcontractor");
        }

        UUID packageId = quote.getPackageId();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        if (pkg.getBoqLineIds() != null && !pkg.getBoqLineIds().isEmpty() && lines != null) {
            for (QuoteLineRequest lr : lines) {
                if (lr.getBoqLineId() != null && !pkg.getBoqLineIds().contains(lr.getBoqLineId())) {
                    throw new BadRequestException("BOQ Line " + lr.getBoqLineId() + " is not part of this package's selected BOQ lines");
                }
            }
        }

        quoteLineRepository.deleteByQuoteId(quote.getId());

        BigDecimal total = BigDecimal.ZERO;
        List<QuoteLineResponse> lineResponses = new ArrayList<>();
        for (QuoteLineRequest lr : lines) {
            QuoteLine line = new QuoteLine();
            line.setQuoteId(quote.getId());
            line.setBoqLineId(lr.getBoqLineId());
            line.setSectionCode(lr.getSectionCode());
            line.setDescription(lr.getDescription());
            line.setQuantity(lr.getQuantity() != null ? lr.getQuantity() : BigDecimal.ZERO);
            line.setUnit(lr.getUnit());
            line.setUnitRate(lr.getUnitRate() != null ? lr.getUnitRate() : BigDecimal.ZERO);
            BigDecimal lineAmount = line.getQuantity().multiply(line.getUnitRate());
            line.setAmount(lineAmount);
            line.setRemarks(lr.getRemarks());

            QuoteLine savedLine = quoteLineRepository.save(line);
            total = total.add(lineAmount);

            QuoteLineResponse lineRes = new QuoteLineResponse();
            lineRes.setId(savedLine.getId());
            lineRes.setBoqLineId(savedLine.getBoqLineId());
            lineRes.setSectionCode(savedLine.getSectionCode());
            lineRes.setDescription(savedLine.getDescription());
            lineRes.setQuantity(savedLine.getQuantity());
            lineRes.setUnit(savedLine.getUnit());
            lineRes.setUnitRate(savedLine.getUnitRate());
            lineRes.setAmount(savedLine.getAmount());
            lineRes.setRemarks(savedLine.getRemarks());
            lineResponses.add(lineRes);
        }

        quote.setTotalAmount(total);
        quote.setStatus("SUBMITTED");
        quote.setSubmittedAt(OffsetDateTime.now());
        quote = quoteRepository.save(quote);

        Subcontractor sc = subcontractorRepository.findById(subcontractorId).orElse(null);

        QuoteResponse res = new QuoteResponse();
        res.setId(quote.getId());
        res.setPackageId(quote.getPackageId());
        res.setSubcontractorId(subcontractorId);
        res.setSubcontractorName(sc != null ? sc.getCompanyName() : "Unknown");
        res.setStatus(quote.getStatus());
        res.setTotalAmount(quote.getTotalAmount());
        res.setRemarks(quote.getRemarks());
        res.setSubmittedAt(quote.getSubmittedAt());
        res.setCreatedAt(quote.getCreatedAt());
        res.setLines(lineResponses);
        return res;
    }

    public TenderPackageResponse openBids(UUID packageId, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        pkg.setBidsOpened(true);
        pkg.setBidsOpenedAt(OffsetDateTime.now());
        pkg.setBidsOpenedBy(currentAccountId);
        pkg.setStatus(PackageStatus.OPENED);

        Package saved = packageRepository.save(pkg);
        return mapToPackageResponse(saved);
    }

    public NormalizedComparisonResponse getNormalizedComparison(UUID packageId, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        boolean isOpened = Boolean.TRUE.equals(pkg.getBidsOpened());

        List<PackageBidder> bidders = packageBidderRepository.findByPackageId(packageId);
        List<Quote> quotes = quoteRepository.findByPackageId(packageId);
        Map<UUID, Quote> quoteBySc = quotes.stream().collect(Collectors.toMap(Quote::getSubcontractorId, q -> q, (q1, q2) -> q1));

        List<NormalizedComparisonResponse.BidderSummary> bidderSummaries = new ArrayList<>();
        for (PackageBidder bidder : bidders) {
            Subcontractor sc = subcontractorRepository.findById(bidder.getSubcontractorId()).orElse(null);
            Quote q = quoteBySc.get(bidder.getSubcontractorId());

            NormalizedComparisonResponse.BidderSummary bs = new NormalizedComparisonResponse.BidderSummary();
            bs.setSubcontractorId(bidder.getSubcontractorId());
            bs.setSubcontractorName(sc != null ? sc.getCompanyName() : "Unknown");
            if (q != null) {
                bs.setQuoteId(q.getId());
                bs.setStatus(q.getStatus());
                bs.setSubmittedAt(q.getSubmittedAt());
                // Mask total amount if bids are not opened yet
                bs.setTotalAmount(isOpened ? q.getTotalAmount() : null);
            } else {
                bs.setStatus(bidder.getStatus());
                bs.setTotalAmount(null);
            }
            bidderSummaries.add(bs);
        }

        // Build line comparison
        List<NormalizedComparisonResponse.ComparisonLine> comparisonLines = new ArrayList<>();
        if (pkg.getBoqDocumentId() != null) {
            List<BoqLine> boqLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(pkg.getBoqDocumentId());
            if (pkg.getBoqLineIds() != null && !pkg.getBoqLineIds().isEmpty()) {
                boqLines = boqLines.stream()
                        .filter(bl -> pkg.getBoqLineIds().contains(bl.getId()))
                        .collect(Collectors.toList());
            }
            for (BoqLine bl : boqLines) {
                NormalizedComparisonResponse.ComparisonLine cl = new NormalizedComparisonResponse.ComparisonLine();
                cl.setBoqLineId(bl.getId());
                cl.setSectionCode(bl.getCategoryCode());
                cl.setDescription(bl.getDescription());
                cl.setQuantity(bl.getQuantity());
                cl.setUnit(bl.getUnit());

                Map<UUID, BigDecimal> ratesMap = new HashMap<>();
                Map<UUID, BigDecimal> amountsMap = new HashMap<>();

                for (PackageBidder bidder : bidders) {
                    Quote q = quoteBySc.get(bidder.getSubcontractorId());
                    if (q != null && isOpened) {
                        List<QuoteLine> qLines = quoteLineRepository.findByQuoteId(q.getId());
                        Optional<QuoteLine> matched = qLines.stream()
                                .filter(l -> l.getBoqLineId() != null && l.getBoqLineId().equals(bl.getId()))
                                .findFirst();
                        if (matched.isPresent()) {
                            ratesMap.put(bidder.getSubcontractorId(), matched.get().getUnitRate());
                            amountsMap.put(bidder.getSubcontractorId(), matched.get().getAmount());
                        }
                    } else if (q != null && !isOpened) {
                        // Sealed bids - rates and amounts remain null
                        ratesMap.put(bidder.getSubcontractorId(), null);
                        amountsMap.put(bidder.getSubcontractorId(), null);
                    }
                }

                cl.setRates(ratesMap);
                cl.setAmounts(amountsMap);
                comparisonLines.add(cl);
            }
        }

        NormalizedComparisonResponse res = new NormalizedComparisonResponse();
        res.setPackageId(pkg.getId());
        res.setPackageRef(pkg.getPackageRef());
        res.setTitle(pkg.getTitle());
        res.setStatus(pkg.getStatus());
        res.setBidsOpened(pkg.getBidsOpened());
        res.setBidders(bidderSummaries);
        res.setLines(comparisonLines);
        return res;
    }

    public PackageClarificationResponse createClarification(UUID packageId, PackageClarificationRequest request, Long currentAccountId) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        PackageClarification pc = new PackageClarification();
        pc.setPackageId(packageId);
        pc.setSubcontractorId(request.getSubcontractorId());
        pc.setAuthorAccountId(currentAccountId);
        pc.setQuestion(request.getQuestion());
        if (request.getAnswer() != null) {
            pc.setAnswer(request.getAnswer());
            pc.setAnsweredBy(currentAccountId);
            pc.setAnsweredAt(OffsetDateTime.now());
        }
        pc.setIsPublicAddendum(request.getSubcontractorId() == null);
        pc = packageClarificationRepository.save(pc);

        return mapToClarificationResponse(pc);
    }

    public List<PackageClarificationResponse> getClarifications(UUID packageId, UUID subcontractorId) {
        List<PackageClarification> all = packageClarificationRepository.findByPackageId(packageId);
        if (subcontractorId == null) {
            return all.stream().map(this::mapToClarificationResponse).collect(Collectors.toList());
        } else {
            return all.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsPublicAddendum()) || subcontractorId.equals(c.getSubcontractorId()))
                    .map(this::mapToClarificationResponse)
                    .collect(Collectors.toList());
        }
    }

    public PackageAddendumResponse issueAddendum(UUID packageId, PackageAddendumRequest request, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        List<PackageAddendum> existing = packageAddendumRepository.findByPackageIdOrderByAddendumNumberAsc(packageId);
        int nextNum = existing.size() + 1;

        PackageAddendum addendum = new PackageAddendum();
        addendum.setPackageId(packageId);
        addendum.setAddendumNumber(nextNum);
        addendum.setTitle(request.getTitle());
        addendum.setDescription(request.getDescription());
        addendum.setAttachmentPath(request.getAttachmentPath());
        addendum.setIssuedBy(currentAccountId);
        addendum.setIssuedAt(OffsetDateTime.now());

        addendum = packageAddendumRepository.save(addendum);

        PackageAddendumResponse res = new PackageAddendumResponse();
        res.setId(addendum.getId());
        res.setPackageId(packageId);
        res.setAddendumNumber(addendum.getAddendumNumber());
        res.setTitle(addendum.getTitle());
        res.setDescription(addendum.getDescription());
        res.setAttachmentPath(addendum.getAttachmentPath());
        res.setIssuedAt(addendum.getIssuedAt());
        res.setIssuedBy(addendum.getIssuedBy());
        res.setAcknowledgedCount(0);
        return res;
    }

    public PackageAddendumAck acknowledgeAddendum(UUID addendumId, UUID subcontractorId, Long accountId) {
        PackageAddendum addendum = packageAddendumRepository.findById(addendumId)
                .orElseThrow(() -> new IllegalArgumentException("Addendum not found: " + addendumId));

        Optional<PackageAddendumAck> existing = packageAddendumAckRepository.findByAddendumIdAndSubcontractorId(addendumId, subcontractorId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PackageAddendumAck ack = new PackageAddendumAck();
        ack.setAddendumId(addendumId);
        ack.setPackageId(addendum.getPackageId());
        ack.setSubcontractorId(subcontractorId);
        ack.setAcknowledgedBy(accountId);
        ack.setAcknowledgedAt(OffsetDateTime.now());
        return packageAddendumAckRepository.save(ack);
    }

    public PackageAwardResponse awardPackage(UUID packageId, PackageAwardRequest request, Long currentAccountId) {
        UUID companyId = CompanyContext.get();
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.getCompanyId().equals(companyId)) {
            throw new SecurityException("Tenant isolation failure");
        }

        Subcontractor sc = subcontractorRepository.findById(request.getSubcontractorId())
                .orElseThrow(() -> new IllegalArgumentException("Subcontractor not found: " + request.getSubcontractorId()));

        PackageAward award = packageAwardRepository.findByPackageId(packageId).orElseGet(PackageAward::new);
        award.setPackageId(packageId);
        award.setSubcontractorId(request.getSubcontractorId());
        award.setWinningQuoteId(request.getWinningQuoteId());
        award.setAgreedAmount(request.getAgreedAmount());
        award.setScope(request.getScope());
        award.setPricedBoqDocumentId(request.getPricedBoqDocumentId());
        award.setDrawingRevision(request.getDrawingRevision());
        award.setProgrammeExtract(request.getProgrammeExtract());
        award.setSiteRules(request.getSiteRules());
        award.setAwardedBy(currentAccountId);
        award.setAwardedAt(OffsetDateTime.now());
        award.setStatus("ISSUED");

        award = packageAwardRepository.save(award);

        pkg.setStatus(PackageStatus.AWARDED);
        packageRepository.save(pkg);

        PackageAwardResponse res = new PackageAwardResponse();
        res.setId(award.getId());
        res.setPackageId(packageId);
        res.setSubcontractorId(award.getSubcontractorId());
        res.setSubcontractorName(sc.getCompanyName());
        res.setWinningQuoteId(award.getWinningQuoteId());
        res.setAgreedAmount(award.getAgreedAmount());
        res.setAwardedAt(award.getAwardedAt());
        res.setAwardedBy(award.getAwardedBy());
        res.setScope(award.getScope());
        res.setPricedBoqDocumentId(award.getPricedBoqDocumentId());
        res.setDrawingRevision(award.getDrawingRevision());
        res.setProgrammeExtract(award.getProgrammeExtract());
        res.setSiteRules(award.getSiteRules());
        res.setStatus(award.getStatus());
        return res;
    }

    public AwardPackResponse getAwardPack(UUID packageId, Long currentAccountId) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        PackageAward award = packageAwardRepository.findByPackageId(packageId)
                .orElseThrow(() -> new IllegalArgumentException("No award found for package: " + packageId));

        Subcontractor sc = subcontractorRepository.findById(award.getSubcontractorId()).orElse(null);

        AwardPackResponse res = new AwardPackResponse();
        res.setAwardId(award.getId());
        res.setPackageId(packageId);
        res.setPackageRef(pkg.getPackageRef());
        res.setTitle(pkg.getTitle());
        res.setSubcontractorId(award.getSubcontractorId());
        res.setSubcontractorName(sc != null ? sc.getCompanyName() : "Unknown");
        res.setAgreedAmount(award.getAgreedAmount());
        res.setScope(award.getScope() != null ? award.getScope() : pkg.getScope());
        res.setPricedBoqDocumentId(award.getPricedBoqDocumentId() != null ? award.getPricedBoqDocumentId() : pkg.getBoqDocumentId());
        res.setDrawingRevision(award.getDrawingRevision() != null ? award.getDrawingRevision() : pkg.getDrawingRevision());
        res.setProgrammeExtract(award.getProgrammeExtract());
        res.setSiteRules(award.getSiteRules());
        res.setAwardedAt(award.getAwardedAt());
        res.setStatus(award.getStatus());

        if (res.getPricedBoqDocumentId() != null) {
            List<BoqLine> bLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(res.getPricedBoqDocumentId());
            if (pkg.getBoqLineIds() != null && !pkg.getBoqLineIds().isEmpty()) {
                bLines = bLines.stream()
                        .filter(bl -> pkg.getBoqLineIds().contains(bl.getId()))
                        .collect(Collectors.toList());
            }
            List<AwardPackResponse.PricedBoqLineSummary> summaries = bLines.stream().map(bl -> {
                AwardPackResponse.PricedBoqLineSummary s = new AwardPackResponse.PricedBoqLineSummary();
                s.setBoqLineId(bl.getId());
                s.setSectionCode(bl.getCategoryCode());
                s.setDescription(bl.getDescription());
                s.setQuantity(bl.getQuantity());
                s.setUnit(bl.getUnit());
                s.setUnitRate(bl.getRate());
                s.setAmount(bl.getAmount());
                return s;
            }).collect(Collectors.toList());
            res.setPricedBoqLines(summaries);
        }

        return res;
    }

    // --- Helpers ---

    private TenderPackageResponse mapToPackageResponse(Package pkg) {
        TenderPackageResponse res = new TenderPackageResponse();
        res.setId(pkg.getId());
        res.setProjectId(pkg.getProjectId());
        res.setCompanyId(pkg.getCompanyId());
        res.setPackageRef(pkg.getPackageRef());
        res.setTitle(pkg.getTitle());
        res.setScope(pkg.getScope());
        res.setBoqDocumentId(pkg.getBoqDocumentId());
        res.setBoqLineIds(pkg.getBoqLineIds());
        res.setTrade(pkg.getTrade());
        res.setDrawingRevision(pkg.getDrawingRevision());
        res.setSpecificationSummary(pkg.getSpecificationSummary());
        res.setSiteVisitInfo(pkg.getSiteVisitInfo());
        res.setTenderDeadline(pkg.getTenderDeadline());
        res.setStatus(pkg.getStatus());
        res.setBidsOpened(pkg.getBidsOpened());
        res.setBidsOpenedAt(pkg.getBidsOpenedAt());
        res.setBidsOpenedBy(pkg.getBidsOpenedBy());
        res.setRequireCarInsurance(pkg.getRequireCarInsurance());
        res.setRequireTplInsurance(pkg.getRequireTplInsurance());
        res.setRequireWorkmenComp(pkg.getRequireWorkmenComp());
        res.setRequireCivilDefence(pkg.getRequireCivilDefence());
        res.setRequireSiraLicence(pkg.getRequireSiraLicence());
        res.setRequireElectricalCert(pkg.getRequireElectricalCert());
        res.setRequiredCommunityReg(pkg.getRequiredCommunityReg());
        res.setCreatedAt(pkg.getCreatedAt());

        List<PackageBidder> bidders = packageBidderRepository.findByPackageId(pkg.getId());
        res.setBidderCount(bidders.size());
        return res;
    }

    private PackageClarificationResponse mapToClarificationResponse(PackageClarification pc) {
        PackageClarificationResponse res = new PackageClarificationResponse();
        res.setId(pc.getId());
        res.setPackageId(pc.getPackageId());
        res.setSubcontractorId(pc.getSubcontractorId());
        if (pc.getSubcontractorId() != null) {
            subcontractorRepository.findById(pc.getSubcontractorId())
                    .ifPresent(sc -> res.setSubcontractorName(sc.getCompanyName()));
        }
        res.setAuthorAccountId(pc.getAuthorAccountId());
        res.setQuestion(pc.getQuestion());
        res.setAnswer(pc.getAnswer());
        res.setAnsweredBy(pc.getAnsweredBy());
        res.setAnsweredAt(pc.getAnsweredAt());
        res.setIsPublicAddendum(pc.getIsPublicAddendum());
        res.setCreatedAt(pc.getCreatedAt());
        return res;
    }

    public EligibilityResult checkEligibility(Package pkg, Subcontractor sc) {
        List<String> reasons = new ArrayList<>();
        boolean isEligible = true;

        // 1. Status check
        if (sc.getStatus() == null || (!sc.getStatus().name().equalsIgnoreCase("APPROVED") && !sc.getStatus().name().equalsIgnoreCase("ACTIVE"))) {
            isEligible = false;
            reasons.add("Subcontractor status is " + sc.getStatus() + " (Must be APPROVED or ACTIVE)");
        }

        // 2. Licence expiry check
        if (sc.getLicenceExpiry() != null && sc.getLicenceExpiry().isBefore(LocalDate.now())) {
            isEligible = false;
            reasons.add("Trade licence expired on " + sc.getLicenceExpiry());
        }

        // 3. Document checks
        List<SubcontractorDocument> docs = subcontractorDocumentRepository.findBySubcontractorId(sc.getId());

        if (Boolean.TRUE.equals(pkg.getRequireCarInsurance())) {
            boolean hasCar = hasValidDocument(docs, "CAR", "CAR_INSURANCE");
            if (!hasCar) {
                isEligible = false;
                reasons.add("Missing or expired CAR Insurance document");
            }
        }

        if (Boolean.TRUE.equals(pkg.getRequireTplInsurance())) {
            boolean hasTpl = hasValidDocument(docs, "TPL", "TPL_INSURANCE");
            if (!hasTpl) {
                isEligible = false;
                reasons.add("Missing or expired TPL Insurance document");
            }
        }

        if (Boolean.TRUE.equals(pkg.getRequireWorkmenComp())) {
            boolean hasWcomp = hasValidDocument(docs, "WORKMEN_COMP", "WORKMENS_COMPENSATION");
            if (!hasWcomp) {
                isEligible = false;
                reasons.add("Missing or expired Workmen's Compensation Insurance document");
            }
        }

        if (Boolean.TRUE.equals(pkg.getRequireCivilDefence())) {
            boolean hasCd = hasValidDocument(docs, "CIVIL_DEFENCE");
            if (!hasCd) {
                isEligible = false;
                reasons.add("Missing or expired Civil Defence approval/licence document");
            }
        }

        if (Boolean.TRUE.equals(pkg.getRequireSiraLicence())) {
            boolean hasSira = hasValidDocument(docs, "SIRA");
            if (!hasSira) {
                isEligible = false;
                reasons.add("Missing or expired SIRA licence document");
            }
        }

        if (Boolean.TRUE.equals(pkg.getRequireElectricalCert())) {
            boolean hasElec = hasValidDocument(docs, "ELECTRICAL_CERT", "DEWA");
            if (!hasElec) {
                isEligible = false;
                reasons.add("Missing or expired Electrical/DEWA certificate document");
            }
        }

        if (pkg.getRequiredCommunityReg() != null && !pkg.getRequiredCommunityReg().isBlank()) {
            boolean hasComm = hasValidDocument(docs, "COMMUNITY_REG", pkg.getRequiredCommunityReg());
            if (!hasComm) {
                isEligible = false;
                reasons.add("Missing or expired Community Registration: " + pkg.getRequiredCommunityReg());
            }
        }

        return new EligibilityResult(isEligible, reasons);
    }

    private boolean hasValidDocument(List<SubcontractorDocument> docs, String... typeMatches) {
        for (SubcontractorDocument doc : docs) {
            if (doc.getDocumentTypeId() == null) continue;
            for (String t : typeMatches) {
                if (doc.getDocumentTypeId().equalsIgnoreCase(t) || (doc.getReferenceNo() != null && doc.getReferenceNo().equalsIgnoreCase(t))) {
                    if (doc.getExpiryDate() == null || !doc.getExpiryDate().isBefore(LocalDate.now())) {
                        if (doc.getStatus() == null || !doc.getStatus().equalsIgnoreCase("REJECTED")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static class EligibilityResult {
        private final boolean eligible;
        private final List<String> reasons;

        public EligibilityResult(boolean eligible, List<String> reasons) {
            this.eligible = eligible;
            this.reasons = reasons;
        }

        public boolean isEligible() {
            return eligible;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }
}
