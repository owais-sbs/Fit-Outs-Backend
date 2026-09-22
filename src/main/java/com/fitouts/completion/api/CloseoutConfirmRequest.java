package com.fitouts.completion.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloseoutConfirmRequest {

    /** When omitted, treated as true. */
    private Boolean confirmed;
}
