package com.fitouts.account.application;

public record ClientAccountConversionResult(
        boolean clientAccountCreated,
        Long clientAccountId,
        String clientEmail,
        String temporaryPassword) {
}
