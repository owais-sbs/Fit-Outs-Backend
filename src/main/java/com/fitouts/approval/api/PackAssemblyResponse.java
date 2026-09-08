package com.fitouts.approval.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** Result of a pack assembly run. */
@Getter
@Builder
public class PackAssemblyResponse {

    private String packFilePath;
    private String packFileName;
    private int documentsCollected;
    /** Documents found on the company or project record and attached without anyone asking. */
    private List<String> autoCollected;
    private List<String> missing;
    private List<String> expired;
    private List<String> waived;
    private boolean readyToSubmit;
    private String note;
}
