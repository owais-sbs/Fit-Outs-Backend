package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteRequest {

    private UUID packageId;
    private String remarks;
    private List<QuoteLineRequest> lines;
}
