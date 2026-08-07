package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RoomScopeDto {

    /** Floor label, e.g. Ground Floor */
    private String floorName;

    /** Rooms belonging to this floor */
    private List<FloorRoomScopeDto> rooms = new ArrayList<>();

    /**
     * Legacy flat shape support (pre floor nesting).
     * When present without {@code rooms}, mapper treats this as a room under General.
     */
    private String roomName;

    /** Legacy selections when using flat {@code roomName}. */
    private List<RoomScopeSelectionDto> selections = new ArrayList<>();
}
