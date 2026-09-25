package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuggestBoqMatchesRequest {
    private UUID templateUuid;
}
