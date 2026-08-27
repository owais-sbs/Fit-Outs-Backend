package com.fitouts.checklist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.RecordingProcessingStatus;
import com.fitouts.checklist.domain.SiteVisitRecording;

public interface SiteVisitRecordingRepository extends JpaRepository<SiteVisitRecording, UUID> {

    List<SiteVisitRecording> findBySiteVisitUuidOrderByCreatedAtAsc(UUID siteVisitUuid);

    List<SiteVisitRecording> findBySiteVisitUuidAndProcessingStatus(
            UUID siteVisitUuid, RecordingProcessingStatus status);
}
