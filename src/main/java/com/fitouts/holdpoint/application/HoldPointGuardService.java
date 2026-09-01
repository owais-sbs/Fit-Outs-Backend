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
     * Blocks schedule progress when an active hold applies to the activity or whole project.
     */
    @Transactional(readOnly = true)
    public void assertProgressAllowed(Long projectId, UUID activityUuid) {
        assertNoBlockingHold(projectId, activityUuid, "Progress blocked by quality hold");
    }

    /**
     * Blocks subcontractor claim submission when the project has any active hold point.
     */
    @Transactional(readOnly = true)
    public void assertClaimAllowed(Long projectId) {
        assertNoBlockingHold(projectId, null, "Claims blocked by quality hold on this project");
    }

    private void assertNoBlockingHold(Long projectId, UUID activityUuid, String prefix) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        for (QualityHoldPoint hold : holdPointRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
                projectId, companyId)) {
            if (hold.getStatus() == HoldPointStatus.CLEARED) {
                continue;
            }
            if (activityUuid == null || hold.getActivityUuid() == null
                    || hold.getActivityUuid().equals(activityUuid)) {
                throw new BadRequestException(
                        prefix + ": " + (hold.getTitle() != null ? hold.getTitle() : "Hold point"));
            }
        }
    }
}
