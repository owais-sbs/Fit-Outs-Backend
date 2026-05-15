package com.fitouts.account.api;

import lombok.Data;

@Data
public class LoginRequest {

    private String email;
    private String password;
}