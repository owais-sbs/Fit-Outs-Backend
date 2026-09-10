package com.fitouts.subcontractor.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.subcontractor.api.ScNotificationPrefRequest;
import com.fitouts.subcontractor.api.ScNotificationPrefResponse;
import com.fitouts.subcontractor.domain.ScPortalNotificationPref;
import com.fitouts.subcontractor.domain.ScPortalNotificationPrefRepository;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScWhatsappOutbox;
import com.fitouts.subcontractor.domain.ScWhatsappOutboxRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScNotificationPrefService {

    private final ScPortalNotificationPrefRepository prefRepository;
    private final ScWhatsappOutboxRepository outboxRepository;
    private final ScPortalAccessService portalAccessService;

    @Transactional(readOnly = true)
    public ScNotificationPrefResponse getPrefs(AuthPrincipal principal) {
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        ScPortalNotificationPref pref = prefRepository.findById(portalUser.getOrganizationUuid())
                .orElseGet(() -> defaultPref(portalUser.getOrganizationUuid()));
        return toResponse(pref);
    }

    @Transactional
    public ScNotificationPrefResponse updatePrefs(AuthPrincipal principal, ScNotificationPrefRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        ScPortalNotificationPref pref = prefRepository.findById(portalUser.getOrganizationUuid())
                .orElseGet(() -> defaultPref(portalUser.getOrganizationUuid()));
        pref.setWhatsappEnabled(request.isWhatsappEnabled());
        pref.setWhatsappNumber(trimToNull(request.getWhatsappNumber()));
        if (StringUtils.hasText(request.getPreferredLanguage())) {
            pref.setPreferredLanguage(request.getPreferredLanguage().trim());
        }
        return toResponse(prefRepository.save(pref));
    }

    @Transactional
    public void queueWhatsApp(AuthPrincipal principal, String messageBody) {
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        ScPortalNotificationPref pref = prefRepository.findById(portalUser.getOrganizationUuid())
                .orElseGet(() -> defaultPref(portalUser.getOrganizationUuid()));
        if (!pref.isWhatsappEnabled()) {
            throw new BadRequestException("WhatsApp notifications are not enabled");
        }
        if (!StringUtils.hasText(messageBody)) {
            throw new BadRequestException("messageBody is required");
        }
        if (!StringUtils.hasText(pref.getWhatsappNumber())) {
            throw new BadRequestException("WhatsApp number is not configured");
        }
        ScWhatsappOutbox row = new ScWhatsappOutbox();
        row.setOrganizationUuid(portalUser.getOrganizationUuid());
        row.setRecipientPhone(pref.getWhatsappNumber());
        row.setMessageBody(messageBody.trim());
        row.setStatus("PENDING");
        outboxRepository.save(row);
    }

    private ScPortalNotificationPref defaultPref(java.util.UUID organizationUuid) {
        ScPortalNotificationPref pref = new ScPortalNotificationPref();
        pref.setOrganizationUuid(organizationUuid);
        pref.setWhatsappEnabled(false);
        pref.setPreferredLanguage("en");
        return pref;
    }

    private ScNotificationPrefResponse toResponse(ScPortalNotificationPref pref) {
        return ScNotificationPrefResponse.builder()
                .organizationUuid(pref.getOrganizationUuid())
                .whatsappEnabled(pref.isWhatsappEnabled())
                .whatsappNumber(pref.getWhatsappNumber())
                .preferredLanguage(pref.getPreferredLanguage())
                .updatedAt(pref.getUpdatedAt())
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
