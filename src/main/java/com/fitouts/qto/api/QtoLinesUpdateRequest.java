package com.fitouts.qto.api;

import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QtoLinesUpdateRequest {
    private List<QtoLineRequest> lines;
}
