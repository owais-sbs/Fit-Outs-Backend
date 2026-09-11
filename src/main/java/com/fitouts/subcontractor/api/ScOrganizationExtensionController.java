package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.shared.web.BaseController;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.subcontractor.application.ScCompanyProfileService;
import com.fitouts.subcontractor.application.ScNotificationPrefService;
import com.fitouts.subcontractor.application.ScOrganizationProfileService;
import com.fitouts.subcontractor.application.ScPortalTeamService;
import com.fitouts.subcontractor.domain.ScOrgDocumentCode;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subcontractor")
@RequiredArgsConstructor
public class ScOrganizationExtensionController extends BaseController {

    private final ScCompanyProfileService profileService;
    private final ScOrganizationProfileService organizationProfileService;
    private final ScPortalTeamService portalTeamService;
    private final ScNotificationPrefService notificationPrefService;

    @GetMapping("/portal-context")
    public Object portalContext() {
        try {
            return successResponse(profileService.getPortalContext());
        } catch (Exception e) {
            return failureResponse("Failed to load portal context", e.getMessage());
        }
    }

    @PutMapping("/organization-extension")
    public Object updateExtension(@RequestBody ScOrganizationExtensionRequest request) {
        try {
            return successResponse(profileService.updateOrganizationExtension(request));
        } catch (Exception e) {
            return failureResponse("Failed to update organization profile", e.getMessage());
        }
    }

    @GetMapping("/community-authorities")
    public Object communityAuthorities() {
        try {
            return successResponse(organizationProfileService.communityAuthorityOptions());
        } catch (Exception e) {
            return failureResponse("Failed to load community authorities", e.getMessage());
        }
    }

    @PutMapping("/bank-detail")
    public Object updateBank(@RequestBody ScBankDetailRequest request) {
        try {
            return successResponse(profileService.updateBankDetail(request));
        } catch (Exception e) {
            return failureResponse("Failed to update bank details", e.getMessage());
        }
    }

    @PostMapping(value = "/bank-detail/letter/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadBankLetter(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse(profileService.uploadBankLetter(file));
        } catch (Exception e) {
            return failureResponse("Failed to upload bank letter", e.getMessage());
        }
    }

    @PostMapping("/community-registrations")
    public Object addCommunity(@RequestBody ScCommunityRegistrationRequest request) {
        try {
            return successResponse(profileService.addCommunityRegistration(request));
        } catch (Exception e) {
            return failureResponse("Failed to add community registration", e.getMessage());
        }
    }

    @PostMapping(value = "/community-registrations/{uuid}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadCommunityFile(@PathVariable UUID uuid, @RequestParam("file") MultipartFile file) {
        try {
            return successResponse(profileService.uploadCommunityFile(uuid, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload community registration file", e.getMessage());
        }
    }

    @DeleteMapping("/community-registrations/{uuid}")
    public Object deleteCommunity(@PathVariable UUID uuid) {
        try {
            profileService.deleteCommunityRegistration(uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete community registration", e.getMessage());
        }
    }

    @PostMapping("/references")
    public Object addReference(@RequestBody ScOrganizationReferenceRequest request) {
        try {
            return successResponse(profileService.addReference(request));
        } catch (Exception e) {
            return failureResponse("Failed to add reference", e.getMessage());
        }
    }

    @PutMapping("/references/{uuid}")
    public Object updateReference(@PathVariable UUID uuid, @RequestBody ScOrganizationReferenceRequest request) {
        try {
            return successResponse(profileService.updateReference(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update reference", e.getMessage());
        }
    }

    @DeleteMapping("/references/{uuid}")
    public Object deleteReference(@PathVariable UUID uuid) {
        try {
            profileService.deleteReference(uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete reference", e.getMessage());
        }
    }

    @PostMapping(value = "/organization-documents/{code}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadOrgDocument(@PathVariable String code, @RequestParam("file") MultipartFile file) {
        try {
            profileService.uploadOrgDocument(ScOrgDocumentCode.valueOf(code.trim().toUpperCase()), file);
            return successResponse("Uploaded", null);
        } catch (Exception e) {
            return failureResponse("Failed to upload document", e.getMessage());
        }
    }

    @GetMapping("/team")
    public Object listTeam() {
        try {
            return successResponse(profileService.listTeam());
        } catch (Exception e) {
            return failureResponse("Failed to load team", e.getMessage());
        }
    }

    @PostMapping("/team/invite")
    public Object inviteTeam(@RequestBody ScInviteTeamMemberRequest request) {
        try {
            return successResponse(profileService.inviteTeamMember(request));
        } catch (Exception e) {
            return failureResponse("Failed to invite team member", e.getMessage());
        }
    }

    @PostMapping("/team/manual-add")
    public Object addTeamManually(@RequestBody ScAddTeamMemberRequest request) {
        try {
            return successResponse(profileService.addTeamMemberManually(request));
        } catch (Exception e) {
            return failureResponse("Failed to add team member", e.getMessage());
        }
    }

    @PutMapping("/team/{uuid}")
    public Object updateTeam(@PathVariable UUID uuid, @RequestBody ScUpdateTeamMemberRequest request) {
        try {
            return successResponse(profileService.updateTeamMember(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update team member", e.getMessage());
        }
    }

    @GetMapping("/notification-prefs")
    public Object getNotificationPrefs() {
        try {
            return successResponse(notificationPrefService.getPrefs(currentPrincipal()));
        } catch (Exception e) {
            return failureResponse("Failed to load notification preferences", e.getMessage());
        }
    }

    @PutMapping("/notification-prefs")
    public Object updateNotificationPrefs(@RequestBody ScNotificationPrefRequest request) {
        try {
            return successResponse(notificationPrefService.updatePrefs(currentPrincipal(), request));
        } catch (Exception e) {
            return failureResponse("Failed to update notification preferences", e.getMessage());
        }
    }

    @PostMapping("/notification-prefs/whatsapp/queue")
    public Object queueWhatsApp(@RequestBody ScWhatsAppQueueRequest request) {
        try {
            notificationPrefService.queueWhatsApp(
                    currentPrincipal(),
                    request != null ? request.getMessageBody() : null);
            return successResponse("Queued", null);
        } catch (Exception e) {
            return failureResponse("Failed to queue WhatsApp message", e.getMessage());
        }
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new IllegalStateException("Authentication required");
        }
        return principal;
    }
}
