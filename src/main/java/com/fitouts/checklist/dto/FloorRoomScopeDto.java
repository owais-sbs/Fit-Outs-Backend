package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FloorRoomScopeDto {

    private String roomName;
    private List<RoomScopeSelectionDto> selections = new ArrayList<>();
}
