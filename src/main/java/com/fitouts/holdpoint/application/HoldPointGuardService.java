package com.fitouts.holdpoint.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.holdpoint.domain.HoldPointStatus;
import com.fitouts.holdpoint.domain.QualityHoldPoint;
import com.fitouts.holdpoint.domain.QualityHoldPointRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldPointGuardService {

    private final QualityHoldPointRepository holdPointRepository;

    /**
     * Blocks schedule progress when an OPEN or HELD hold applies to the activity
     * (or is project-wide with no activity link). Planned OPEN holds on other
     * activities do not block.
     */
    @Transactional(readOnly = true)
    public void assertProgressAllowed(Long projectId, UUID activityUuid) {
        assertNoBlockingHold(projectId, activityUuid, false,
                "Progress blocked by quality hold");
    }

    /**
     * Blocks subcontractor claim submission only when QA has explicitly marked a
     * hold as {@link HoldPointStatus#HELD}. Programme-cascaded OPEN checkpoints
     * (e.g. future handover / DLP) must not freeze interim claims.
     */
    @Transactional(readOnly = true)
    public void assertClaimAllowed(Long projectId) {
        assertNoBlockingHold(projectId, null, true,
                "Claims blocked by quality hold on this project");
    }

    private void assertNoBlockingHold(Long projectId, UUID activityUuid, boolean claimsOnlyHeld, String prefix) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        for (QualityHoldPoint hold : holdPointRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
                projectId, companyId)) {
            if (hold.getStatus() == HoldPointStatus.CLEARED) {
                continue;
            }

            if (claimsOnlyHeld) {
                if (hold.getStatus() != HoldPointStatus.HELD) {
                    continue;
                }
                throw new BadRequestException(prefix + ": " + holdTitle(hold));
            }

            // Progress: OPEN or HELD on this activity (or project-wide hold).
            if (hold.getStatus() != HoldPointStatus.OPEN && hold.getStatus() != HoldPointStatus.HELD) {
                continue;
            }
            if (hold.getActivityUuid() != null && activityUuid != null
                    && !hold.getActivityUuid().equals(activityUuid)) {
                continue;
            }
            throw new BadRequestException(prefix + ": " + holdTitle(hold));
        }
    }

    private static String holdTitle(QualityHoldPoint hold) {
        return hold.getTitle() != null ? hold.getTitle() : "Hold point";
    }
}
