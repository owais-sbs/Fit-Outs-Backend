package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScBankDetailRequest {

    private String bankName;
    private String accountName;
    private String iban;
    private String swiftCode;
}
