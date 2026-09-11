package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScInviteVendorRequest {

    private String email;
    private String fullName;
    private String companyName;
    private String phone;
}
