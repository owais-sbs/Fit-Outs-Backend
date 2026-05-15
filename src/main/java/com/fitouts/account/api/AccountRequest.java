package com.fitouts.account.api;

import lombok.Data;

@Data
public class AccountRequest {

    private String fullName;
    private String email;
    private String password;
    private String phone;
    private String companyName;
}