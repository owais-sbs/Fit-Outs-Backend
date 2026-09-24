package com.fitouts.subcontractor.domain;

public enum ScPaymentCertificateStatus {
    /** Awaiting Module 23 matrix / internal approval. */
    DRAFT,
    /** QS certified; awaiting Finance/Director mark-payable when no matrix. */
    ISSUED,
    /** Approved for payment — invoices may be raised against this certificate. */
    PAYABLE,
    PAID
}
