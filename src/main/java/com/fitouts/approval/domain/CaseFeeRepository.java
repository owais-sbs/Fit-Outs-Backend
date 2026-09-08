package com.fitouts.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseFeeRepository extends JpaRepository<CaseFee, UUID> {

    List<CaseFee> findByCaseUuidOrderByCreatedAtAsc(UUID caseUuid);

    List<CaseFee> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** Refundable amounts a third party still holds. This is the deposit ledger. */
    @Query("SELECT f FROM CaseFee f WHERE f.companyId = :companyId AND f.refundable = true "
            + "AND f.paidDate IS NOT NULL AND f.refundReceivedDate IS NULL "
            + "ORDER BY f.paidDate ASC")
    List<CaseFee> findOutstandingDeposits(@Param("companyId") UUID companyId);

    /** Every deposit ever paid, refunded or not, for the full ledger view. */
    @Query("SELECT f FROM CaseFee f WHERE f.companyId = :companyId AND f.refundable = true "
            + "ORDER BY f.paidDate DESC NULLS LAST")
    List<CaseFee> findAllDeposits(@Param("companyId") UUID companyId);
}
