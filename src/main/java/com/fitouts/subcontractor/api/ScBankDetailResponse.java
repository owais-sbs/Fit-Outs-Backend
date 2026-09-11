package com.fitouts.subcontractor.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScBankDetailResponse {

    private final String bankName;
    private final String accountName;
    private final String iban;
    private final String swiftCode;
    private final String bankLetterFilePath;
    private final String verificationStatus;
}
