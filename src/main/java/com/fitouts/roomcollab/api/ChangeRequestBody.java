package com.fitouts.roomcollab.api;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRequestBody {
    private String notes;
    private UUID versionUuid;
}
