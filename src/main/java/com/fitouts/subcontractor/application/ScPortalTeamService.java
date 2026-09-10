package com.fitouts.subcontractor.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.application.ClientAccountConversionResult;
import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScAddTeamMemberRequest;
import com.fitouts.subcontractor.api.ScInviteTeamMemberRequest;
import com.fitouts.subcontractor.api.ScPortalTeamMemberResponse;
import com.fitouts.subcontractor.api.ScUpdateTeamMemberRequest;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScPortalUserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScPortalTeamService {

    private final ScPortalUserRepository portalUserRepository;
    private final ScOrganizationResolver organizationResolver;
    private final ScPortalAccessService portalAccessService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final ClientPortalInviteService portalInviteService;

    @Transactional(readOnly = true)
    public List<ScPortalTeamMemberResponse> listTeam(AuthPrincipal principal) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = organizationResolver.resolveOrganization(principal.getAccountId());
        return portalUserRepository.findByOrganizationUuidOrderByCreatedAtAsc(org.getUuid())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScPortalTeamMemberResponse inviteTeamMember(AuthPrincipal principal, ScInviteTeamMemberRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new BadRequestException("Team member email is required");
        }
        ScPortalRole role = parseRole(request.getPortalRole());
        if (role == ScPortalRole.SC_ADMIN) {
            throw new BadRequestException("Use the primary vendor invite flow for SC Admin accounts");
        }
        ScOrganization org = organizationResolver.resolveOrganization(principal.getAccountId());
        ClientAccountConversionResult accountResult = accountService.createOrUpdateSubcontractorAccount(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                org.getLegalCompanyName());

        if (portalUserRepository.findByAccountId(accountResult.clientAccountId()).isPresent()) {
            throw new BadRequestException("This email already has portal access");
        }

        ScPortalUser portalUser = new ScPortalUser();
        portalUser.setOrganizationUuid(org.getUuid());
        portalUser.setAccountId(accountResult.clientAccountId());
        portalUser.setPortalRole(role);
        portalUser.setStatus(ScPortalUserStatus.INVITED);
        portalUser.setInvitedByAccountId(principal.getAccountId());
        portalUserRepository.save(portalUser);

        String inviteName = StringUtils.hasText(request.getFullName())
                ? request.getFullName().trim()
                : request.getEmail().trim();
        portalInviteService.sendSubcontractorPortalInvite(accountResult.clientAccountId(), inviteName);
        return toResponse(portalUser);
    }

    /** Manual add with password — no invite email. */
    @Transactional
    public ScPortalTeamMemberResponse addTeamMemberManually(AuthPrincipal principal, ScAddTeamMemberRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new BadRequestException("Team member email is required");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BadRequestException("Password is required for manual team member creation");
        }
        ScPortalRole role = parseRole(request.getPortalRole());
        if (role == ScPortalRole.SC_ADMIN) {
            throw new BadRequestException("Use the primary vendor invite flow for SC Admin accounts");
        }
        ScOrganization org = organizationResolver.resolveOrganization(principal.getAccountId());

        Optional<Long> existingAccountId = accountRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .map(Account::getId);
        if (existingAccountId.isPresent()
                && portalUserRepository.findByAccountId(existingAccountId.get()).isPresent()) {
            throw new BadRequestException("This email already has portal access");
        }

        ClientAccountConversionResult accountResult = accountService.createOrUpdateSubcontractorAccountWithPassword(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                org.getLegalCompanyName(),
                request.getPassword());

        if (portalUserRepository.findByAccountId(accountResult.clientAccountId()).isPresent()) {
            throw new BadRequestException("This email already has portal access");
        }

        ScPortalUser portalUser = new ScPortalUser();
        portalUser.setOrganizationUuid(org.getUuid());
        portalUser.setAccountId(accountResult.clientAccountId());
        portalUser.setPortalRole(role);
        portalUser.setStatus(ScPortalUserStatus.ACTIVE);
        portalUser.setInvitedByAccountId(principal.getAccountId());
        return toResponse(portalUserRepository.save(portalUser));
    }

    @Transactional
    public ScPortalTeamMemberResponse updateTeamMember(
            AuthPrincipal principal, UUID portalUserUuid, ScUpdateTeamMemberRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = organizationResolver.resolveOrganization(principal.getAccountId());
        ScPortalUser portalUser = portalUserRepository.findById(portalUserUuid)
                .filter(u -> u.getOrganizationUuid().equals(org.getUuid()))
                .orElseThrow(() -> new NotFoundException("Team member not found"));
        if (portalUser.getAccountId().equals(principal.getAccountId())) {
            throw new BadRequestException("You cannot change your own portal role or status here");
        }
        if (request != null) {
            if (StringUtils.hasText(request.getPortalRole())) {
                ScPortalRole role = parseRole(request.getPortalRole());
                if (role == ScPortalRole.SC_ADMIN && portalUser.getPortalRole() != ScPortalRole.SC_ADMIN) {
                    throw new BadRequestException("Cannot promote another user to SC Admin");
                }
                portalUser.setPortalRole(role);
            }
            if (StringUtils.hasText(request.getStatus())) {
                portalUser.setStatus(ScPortalUserStatus.valueOf(request.getStatus().trim().toUpperCase()));
            }
        }
        return toResponse(portalUserRepository.save(portalUser));
    }

    private ScPortalTeamMemberResponse toResponse(ScPortalUser portalUser) {
        Account account = accountRepository.findById(portalUser.getAccountId()).orElse(null);
        return ScPortalTeamMemberResponse.builder()
                .uuid(portalUser.getUuid())
                .accountId(portalUser.getAccountId())
                .fullName(account != null ? account.getFullName() : null)
                .email(account != null ? account.getEmail() : null)
                .portalRole(portalUser.getPortalRole().name())
                .status(portalUser.getStatus().name())
                .build();
    }

    private static ScPortalRole parseRole(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ScPortalRole.SC_SUPERVISOR;
        }
        try {
            return ScPortalRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid portal role: " + raw);
        }
    }
}
