package com.fitouts.reporting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitouts.schedule.api.ScheduleActivityResponse;

class FinalProjectReportServiceTest {

    @Test
    void fileNameUsesTheOpenProjectAndDate() {
        assertEquals(
                "Marina_Tower_Final_Project_Report_2026-10-07.pdf",
                FinalProjectReportService.fileName("Marina Tower", LocalDate.of(2026, 10, 7)));
        assertTrue(FinalProjectReportService.fileName("Project 57 / Humaid", LocalDate.of(2026, 1, 2))
                .startsWith("Project_57_Humaid_Final_Project_Report_"));
    }

    @Test
    void weightedProgressUsesActivityWeightsOnly() {
        ScheduleActivityResponse done = ScheduleActivityResponse.builder()
                .percentComplete(100)
                .weight(BigDecimal.ONE)
                .build();
        ScheduleActivityResponse open = ScheduleActivityResponse.builder()
                .percentComplete(0)
                .weight(BigDecimal.ONE)
                .build();
        assertEquals(new BigDecimal("50.00"), FinalProjectReportService.weighted(List.of(done, open)));
    }
}
