package com.fitouts.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.notification.api.NotificationResponse;
import com.fitouts.notification.domain.InAppNotification;
import com.fitouts.notification.domain.InAppNotificationRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.error.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-app alerts, with an optional email copy.
 *
 * <p>Alerts are deduplicated by key so the daily expiry sweep does not re-notify the same
 * permit at the same threshold every morning. Email failures never fail the caller: a
 * scheduling recalculation must not roll back because a mail server was down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final InAppNotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;

    /** Builder-free entry point for the common case. */
    public record Alert(
            UUID companyId,
            Long accountId,
            String category,
            String severity,
            String title,
            String body,
            String linkPath,
            String sourceType,
            UUID sourceUuid,
            String dedupeKey,
            boolean alsoEmail) {
    }

    /**
     * Records an alert and optionally emails it.
     *
     * @return false when an alert with the same dedupe key already exists
     */
    @Transactional
    public boolean raise(Alert alert) {
        if (alert.dedupeKey() != null && notificationRepository.existsByDedupeKey(alert.dedupeKey())) {
            return false;
        }

        InAppNotification notification = new InAppNotification();
        notification.setCompanyId(alert.companyId());
        notification.setAccountId(alert.accountId());
        notification.setCategory(alert.category());
        notification.setSeverity(alert.severity() != null ? alert.severity() : "INFO");
        notification.setTitle(alert.title());
        notification.setBody(alert.body());
        notification.setLinkPath(alert.linkPath());
        notification.setSourceType(alert.sourceType());
        notification.setSourceUuid(alert.sourceUuid());
        notification.setDedupeKey(alert.dedupeKey());
        notificationRepository.save(notification);

        if (alert.alsoEmail() && alert.accountId() != null) {
            emailQuietly(alert);
        }
        return true;
    }

    private void emailQuietly(Alert alert) {
        try {
            Account account = accountRepository.findById(alert.accountId()).orElse(null);
            if (account == null || account.getEmail() == null || account.getEmail().isBlank()) {
                return;
            }
            emailService.sendAsync(EmailMessage.builder()
                    .to(account.getEmail())
                    .subject(alert.title())
                    .body(alert.body() != null ? alert.body() : alert.title())
                    .html(false)
                    .build());
        } catch (Exception e) {
            log.warn("Could not email notification \"{}\": {}", alert.title(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(int limit) {
        AuthPrincipal principal = requirePrincipal();
        UUID companyId = requireCompany();
        return notificationRepository
                .findForAccount(companyId, principal.getAccountId(), PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        AuthPrincipal principal = requirePrincipal();
        return notificationRepository.countUnread(requireCompany(), principal.getAccountId());
    }

    @Transactional
    public int markAllRead() {
        AuthPrincipal principal = requirePrincipal();
        return notificationRepository.markAllRead(requireCompany(), principal.getAccountId());
    }

    @Transactional
    public void markRead(UUID uuid) {
        notificationRepository.findById(uuid).ifPresent(n -> {
            if (n.getReadAt() == null) {
                n.setReadAt(java.time.OffsetDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    private NotificationResponse toResponse(InAppNotification n) {
        return NotificationResponse.builder()
                .uuid(n.getUuid())
                .category(n.getCategory())
                .severity(n.getSeverity())
                .title(n.getTitle())
                .body(n.getBody())
                .linkPath(n.getLinkPath())
                .sourceType(n.getSourceType())
                .sourceUuid(n.getSourceUuid())
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .build();
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }
}
