package com.fitouts.boq.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoqProjectRules {

    private final BoqDocumentRepository boqDocumentRepository;

    public List<BoqDocument> listForProject(Long projectId) {
        return boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public Optional<BoqDocument> findApproved(Long projectId) {
        return listForProject(projectId).stream()
                .filter(this::isApproved)
                .max(Comparator.comparing(BoqDocument::getCreatedAt));
    }

    public Optional<BoqDocument> findLive(Long projectId) {
        List<BoqDocument> all = listForProject(projectId);
        Optional<BoqDocument> approved = all.stream()
                .filter(this::isApproved)
                .max(Comparator.comparing(BoqDocument::getCreatedAt));
        if (approved.isPresent()) {
            return approved;
        }
        return all.stream()
                .filter(d -> d.getStatus() != BoqDocumentStatus.OBSOLETE)
                .max(Comparator.comparing(BoqDocument::getCreatedAt));
    }

    public Optional<BoqDocument> findLiveDraft(Long projectId) {
        return findLive(projectId).filter(d -> d.getStatus() == BoqDocumentStatus.DRAFT);
    }

    public boolean isFrozen(Long projectId) {
        return findApproved(projectId).isPresent();
    }

    public void assertNotFrozen(Long projectId) {
        if (isFrozen(projectId)) {
            throw new BadRequestException(
                    "This project already has an approved BOQ. Further BOQs cannot be submitted or approved.");
        }
    }

    public void assertNotObsolete(BoqDocument doc) {
        if (doc.getStatus() == BoqDocumentStatus.OBSOLETE) {
            throw new BadRequestException("This BOQ version is obsolete");
        }
    }

    public boolean isApproved(BoqDocument doc) {
        BoqDocumentStatus status = doc.getStatus();
        return status == BoqDocumentStatus.APPROVED || status == BoqDocumentStatus.FINAL;
    }

    public boolean isPending(BoqDocument doc) {
        BoqDocumentStatus status = doc.getStatus();
        return status == BoqDocumentStatus.PENDING_SENIOR_QS
                || status == BoqDocumentStatus.PENDING_PM
                || status == BoqDocumentStatus.PENDING_DIRECTOR
                || status == BoqDocumentStatus.PENDING_CLIENT;
    }

    public UUID rootId(BoqDocument doc) {
        return doc.getParentBoq() != null ? doc.getParentBoq().getId() : doc.getId();
    }

    public UUID rootIdForProject(Long projectId) {
        List<BoqDocument> all = listForProject(projectId);
        return all.stream()
                .filter(d -> d.getParentBoq() != null)
                .map(d -> d.getParentBoq().getId())
                .findFirst()
                .orElseGet(() -> all.stream()
                        .min(Comparator.comparing(BoqDocument::getCreatedAt))
                        .map(BoqDocument::getId)
                        .orElse(null));
    }

    public String nextVersion(Long projectId) {
        String latest = listForProject(projectId).stream()
                .max(Comparator.comparing(BoqDocument::getCreatedAt))
                .map(BoqDocument::getVersion)
                .filter(StringUtils::hasText)
                .orElse("1.0");
        return incrementVersion(latest);
    }

    public void obsoleteOthers(Long projectId, UUID keepId) {
        UUID root = null;
        for (BoqDocument doc : listForProject(projectId)) {
            if (doc.getId().equals(keepId)) {
                root = rootId(doc);
                continue;
            }
            if (doc.getStatus() == BoqDocumentStatus.OBSOLETE) {
                continue;
            }
            doc.setStatus(BoqDocumentStatus.OBSOLETE);
            doc.setCurrentApprovalStep(null);
            if (doc.getParentBoq() == null && root != null && !doc.getId().equals(root)) {
                doc.setParentBoq(boqDocumentRepository.getReferenceById(root));
            }
            boqDocumentRepository.save(doc);
        }
    }

    public String incrementVersion(String current) {
        if (!StringUtils.hasText(current)) {
            return "1.1";
        }
        String[] parts = current.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            minor += 1;
            return major + "." + minor;
        } catch (NumberFormatException e) {
            return current + ".1";
        }
    }
}
