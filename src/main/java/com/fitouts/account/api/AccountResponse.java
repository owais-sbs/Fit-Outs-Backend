package com.fitouts.account.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccountResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
}