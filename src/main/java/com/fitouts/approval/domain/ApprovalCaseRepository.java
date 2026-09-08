package com.fitouts.approval.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalCaseRepository extends JpaRepository<ApprovalCase, UUID> {

    List<ApprovalCase> findByProjectIdAndCompanyIdOrderByCreatedAtAsc(Long projectId, UUID companyId);

    List<ApprovalCase> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ApprovalCase> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    Optional<ApprovalCase> findByProjectIdAndCompanyIdAndPermitTypeCode(
            Long projectId, UUID companyId, String permitTypeCode);

    long countByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndCaseNumber(UUID companyId, String caseNumber);

    /** Live permits due to expire on or before the cutoff, for the daily alert sweep. */
    @Query("SELECT c FROM ApprovalCase c WHERE c.expiryDate IS NOT NULL AND c.expiryDate <= :cutoff "
            + "AND c.status IN (com.fitouts.approval.domain.ApprovalCaseStatus.ISSUED, "
            + "com.fitouts.approval.domain.ApprovalCaseStatus.EXPIRING_SOON, "
            + "com.fitouts.approval.domain.ApprovalCaseStatus.APPROVED)")
    List<ApprovalCase> findExpiringOnOrBefore(@Param("cutoff") LocalDate cutoff);

    /** Cases where the authority has passed its SLA without an outcome. */
    @Query("SELECT c FROM ApprovalCase c WHERE c.slaDueDate IS NOT NULL AND c.slaDueDate < :today "
            + "AND c.status IN (com.fitouts.approval.domain.ApprovalCaseStatus.SUBMITTED, "
            + "com.fitouts.approval.domain.ApprovalCaseStatus.UNDER_REVIEW, "
            + "com.fitouts.approval.domain.ApprovalCaseStatus.RESUBMITTED)")
    List<ApprovalCase> findOverdueSla(@Param("today") LocalDate today);

    @Query("SELECT c FROM ApprovalCase c WHERE c.companyId = :companyId "
            + "AND c.permitTypeCode = :permitTypeCode AND c.approvedDate IS NOT NULL")
    List<ApprovalCase> findApprovedByPermitType(@Param("companyId") UUID companyId,
                                                @Param("permitTypeCode") String permitTypeCode);
}
