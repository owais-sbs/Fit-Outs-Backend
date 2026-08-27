package com.fitouts.shared.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.error.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Value("${fitouts.gemini.api-key:}")
    private String apiKey;

    @Value("${fitouts.gemini.model:gemini-2.0-flash}")
    private String model;

    public TranscriptionResult transcribeAndSummarize(String relativeAudioPath) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Gemini API key is not configured");
        }
        Path audioFile = fileStorageService.resolve(relativeAudioPath);
        if (!Files.exists(audioFile)) {
            throw new BadRequestException("Audio file not found");
        }

        String mimeType = detectMimeType(relativeAudioPath);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(audioFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file", e);
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        String prompt = """
                Listen to this site visit field recording from a fit-out / renovation project.
                Return ONLY valid JSON with exactly these keys:
                - "transcript": full verbatim transcription of all speech
                - "summary": concise professional summary (bullet points of key observations, rooms discussed, issues, and action items)
                Do not wrap the JSON in markdown fences.
                """;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", base64)),
                                Map.of("text", prompt)))));

        RestClient client = RestClient.create();
        String url = API_BASE + model + ":generateContent?key=" + apiKey;

        String responseText = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseResult(responseText);
    }

    private TranscriptionResult parseResult(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty()) {
                throw new BadRequestException("Gemini returned no candidates");
            }
            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            text = text.trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode parsed = objectMapper.readTree(text);
            return new TranscriptionResult(
                    parsed.path("transcript").asText(""),
                    parsed.path("summary").asText(""));
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseJson, e);
            throw new BadRequestException("Failed to parse AI transcription response");
        }
    }

    private String detectMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        return "audio/webm";
    }

    public record TranscriptionResult(String transcript, String summary) {}
}
