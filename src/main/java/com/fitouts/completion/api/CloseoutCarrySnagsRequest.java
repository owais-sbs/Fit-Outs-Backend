package com.fitouts.completion.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloseoutCarrySnagsRequest {

    private List<UUID> snagUuids = new ArrayList<>();
}
