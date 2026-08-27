package com.fitouts.checklist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.checklist.domain.RecordingProcessingStatus;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitRecording;
import com.fitouts.checklist.dto.SiteVisitAttachmentUploadResponse;
import com.fitouts.checklist.dto.SiteVisitRecordingResponse;
import com.fitouts.checklist.repository.SiteVisitRecordingRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteVisitRecordingService {

    private final SiteVisitRecordingRepository repository;
    private final SiteVisitService siteVisitService;
    private final FileStorageService fileStorageService;
    private final SiteVisitTranscriptionWorker transcriptionWorker;

    @Transactional
    public SiteVisitRecordingResponse upload(UUID siteVisitUuid, MultipartFile file, Integer durationSeconds) {
        SiteVisit visit = siteVisitService.getSiteVisit(siteVisitUuid);
        String subfolder = "site-visits/" + siteVisitUuid;
        String path = fileStorageService.store(file, subfolder);

        SiteVisitRecording recording = new SiteVisitRecording();
        recording.setSiteVisit(visit);
        recording.setAudioPath(path);
        recording.setDurationSeconds(durationSeconds);
        recording.setProcessingStatus(RecordingProcessingStatus.PENDING);

        return toResponse(repository.save(recording));
    }

    @Transactional(readOnly = true)
    public List<SiteVisitRecordingResponse> listByVisit(UUID siteVisitUuid) {
        siteVisitService.getSiteVisit(siteVisitUuid);
        return repository.findBySiteVisitUuidOrderByCreatedAtAsc(siteVisitUuid).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteVisitRecordingResponse getByUuid(UUID recordingUuid) {
        return toResponse(repository.findById(recordingUuid)
                .orElseThrow(() -> new NotFoundException("Recording not found")));
    }

    public void processPendingForVisit(UUID siteVisitUuid) {
        List<SiteVisitRecording> pending = repository.findBySiteVisitUuidAndProcessingStatus(
                siteVisitUuid, RecordingProcessingStatus.PENDING);
        for (SiteVisitRecording recording : pending) {
            transcriptionWorker.processRecording(recording.getUuid());
        }
    }

    @Transactional
    public SiteVisitAttachmentUploadResponse uploadPhoto(UUID siteVisitUuid, MultipartFile file) {
        SiteVisit visit = siteVisitService.getSiteVisit(siteVisitUuid);
        String subfolder = "site-visits/" + siteVisitUuid + "/photos";
        String path = fileStorageService.store(file, subfolder);
        String contentType = file.getContentType();
        return SiteVisitAttachmentUploadResponse.builder()
                .url(toPublicUrl(path))
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build();
    }

    public String toPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return "";
        return "/api/files/" + relativePath;
    }

    private SiteVisitRecordingResponse toResponse(SiteVisitRecording recording) {
        return SiteVisitRecordingResponse.builder()
                .uuid(recording.getUuid())
                .siteVisitUuid(recording.getSiteVisit().getUuid())
                .audioUrl(toPublicUrl(recording.getAudioPath()))
                .durationSeconds(recording.getDurationSeconds())
                .transcript(recording.getTranscript())
                .aiSummary(recording.getAiSummary())
                .processingStatus(recording.getProcessingStatus().name())
                .createdAt(recording.getCreatedAt())
                .build();
    }
}
