package com.fitouts.billing.application;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.billing.domain.BillingMilestone;
import com.fitouts.billing.domain.PaymentRequest;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectTeamAssignment;
import com.fitouts.project.domain.ProjectTeamAssignmentRepository;
import com.fitouts.project.domain.ProjectTeamRole;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPaymentEmailService {

    private static final DecimalFormat AED = new DecimalFormat("#,##0.00");

    private final AccountRepository accountRepository;
    private final LeadRepository leadRepository;
    private final ProjectTeamAssignmentRepository teamAssignmentRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.public-url:http://localhost:3000}")
    private String publicUrl;

    public record SendResult(boolean sent, String clientEmail) {
      public  static SendResult skipped() {
            return new SendResult(false, null);
        }

       public static SendResult failed(String email) {
            return new SendResult(false, email);
        }

       public static SendResult ok(String email) {
            return new SendResult(true, email);
        }
    }

    public SendResult notifyClient(Project project, BillingMilestone milestone, PaymentRequest paymentRequest) {
        log.info(
                "Resolving client email for project {} (clientId={}, leadId={})",
                project.getId(),
                project.getClientId(),
                project.getLeadId());
        ClientRecipient recipient = resolveClient(project);
        if (recipient == null || !StringUtils.hasText(recipient.email())) {
            log.info("No client email for project {} — payment request email skipped", project.getId());
            return SendResult.skipped();
        }

        String invoicesUrl = trimTrailingSlash(publicUrl) + "/client/invoices";
        try {
            String html = emailTemplateService.render("payment-request-to-client", Map.of(
                    "clientName", recipient.name(),
                    "projectName", project.getName() != null ? project.getName() : "your project",
                    "milestoneName", milestone.getName() != null ? milestone.getName() : "Milestone",
                    "amount", formatAed(paymentRequest.getAmount()),
                    "dueDate", milestone.getDueDate() != null ? milestone.getDueDate().toString() : "—",
                    "invoicesUrl", invoicesUrl
            ));

            emailService.send(EmailMessage.builder()
                    .to(recipient.email())
                    .subject("Payment reminder — " + project.getName() + " / " + milestone.getName())
                    .body(html)
                    .html(true)
                    .build());
            return SendResult.ok(recipient.email());
        } catch (Exception e) {
            log.warn("Failed to email payment request to {}: {}", recipient.email(), e.getMessage());
            return SendResult.failed(recipient.email());
        }
    }

    private ClientRecipient resolveClient(Project project) {
        if (project.getClientId() != null) {
            ClientRecipient fromAccount = recipientFromAccountId(project.getClientId(), project.getCompanyId());
            if (fromAccount != null) {
                return fromAccount;
            }
            log.warn(
                    "Project {} has clientId {} but no client account email was found",
                    project.getId(),
                    project.getClientId());
        }

        if (project.getCompanyId() != null) {
            ClientRecipient fromTeam = teamAssignmentRepository
                    .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(project.getId(), project.getCompanyId())
                    .stream()
                    .filter(a -> a.getRole() == ProjectTeamRole.CLIENT)
                    .map(this::recipientFromAssignment)
                    .filter(r -> r != null)
                    .findFirst()
                    .orElse(null);
            if (fromTeam != null) {
                return fromTeam;
            }
        }

        if (project.getLeadId() != null) {
            Lead lead = leadRepository.findById(project.getLeadId()).orElse(null);
            if (lead != null && StringUtils.hasText(lead.getEmail())) {
                String name = StringUtils.hasText(lead.getClientName()) ? lead.getClientName().trim() : "there";
                return new ClientRecipient(lead.getEmail().trim(), name);
            }
        }
        return null;
    }

    private ClientRecipient recipientFromAccountId(Long accountId, java.util.UUID companyId) {
        if (accountId == null) {
            return null;
        }
        if (companyId != null) {
            ClientRecipient scoped = accountRepository.findByIdAndCompanyUuid(accountId, companyId)
                    .map(this::recipientFromAccount)
                    .orElse(null);
            if (scoped != null) {
                return scoped;
            }
        }
        return accountRepository.findById(accountId)
                .map(this::recipientFromAccount)
                .orElse(null);
    }

    private ClientRecipient recipientFromAccount(Account account) {
        if (account == null || !StringUtils.hasText(account.getEmail())) {
            return null;
        }
        String name = StringUtils.hasText(account.getFullName()) ? account.getFullName().trim() : "there";
        return new ClientRecipient(account.getEmail().trim(), name);
    }

    private ClientRecipient recipientFromAssignment(ProjectTeamAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        if (StringUtils.hasText(assignment.getEmail())) {
            String name = StringUtils.hasText(assignment.getDisplayName())
                    ? assignment.getDisplayName().trim()
                    : "there";
            return new ClientRecipient(assignment.getEmail().trim(), name);
        }
        return recipientFromAccountId(assignment.getAccountId(), assignment.getCompanyId());
    }

    private static String formatAed(BigDecimal amount) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        return "AED " + AED.format(value);
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "http://localhost:3000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record ClientRecipient(String email, String name) {}
}
