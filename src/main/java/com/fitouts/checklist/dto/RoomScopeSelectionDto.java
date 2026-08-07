package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomScopeSelectionDto {

    private String category;
    private List<String> items = new ArrayList<>();
}
