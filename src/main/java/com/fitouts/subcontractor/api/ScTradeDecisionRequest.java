package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScTradeDecisionRequest {

    /** APPROVE or REJECT */
    private String decision;
    private String trade;
    private String reason;
}
