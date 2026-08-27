package com.fitouts.checklist.service;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fitouts.checklist.domain.RecordingProcessingStatus;
import com.fitouts.checklist.domain.SiteVisitRecording;
import com.fitouts.checklist.repository.SiteVisitRecordingRepository;
import com.fitouts.shared.ai.GeminiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteVisitTranscriptionWorker {

    private final SiteVisitRecordingRepository repository;
    private final GeminiService geminiService;

    @Async("taskExecutor")
    public void processRecording(UUID recordingUuid) {
        SiteVisitRecording recording = repository.findById(recordingUuid).orElse(null);
        if (recording == null) return;

        recording.setProcessingStatus(RecordingProcessingStatus.PROCESSING);
        repository.save(recording);

        try {
            GeminiService.TranscriptionResult result =
                    geminiService.transcribeAndSummarize(recording.getAudioPath());
            recording.setTranscript(result.transcript());
            recording.setAiSummary(result.summary());
            recording.setProcessingStatus(RecordingProcessingStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Transcription failed for recording {}", recordingUuid, e);
            recording.setProcessingStatus(RecordingProcessingStatus.FAILED);
            recording.setAiSummary("Transcription failed: " + e.getMessage());
        }
        repository.save(recording);
    }
}
