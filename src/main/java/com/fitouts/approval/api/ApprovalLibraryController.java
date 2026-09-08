package com.fitouts.approval.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.approval.application.ApprovalLibraryService;
import com.fitouts.approval.application.ApprovalSeedImportService;
import com.fitouts.approval.application.SeedFileLocator;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalLibraryController extends BaseController {

    private final ApprovalLibraryService libraryService;
    private final ApprovalSeedImportService seedImportService;
    private final SeedFileLocator seedFileLocator;

    @GetMapping("/authorities")
    public Object listAuthorities(
            @RequestParam(required = false) String emirate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        try {
            return successResponse(libraryService.listAuthorities(emirate, type, search));
        } catch (Exception e) {
            return failureResponse("Failed to list authorities", e.getMessage());
        }
    }

    @PostMapping("/authorities")
    public Object createAuthority(@RequestBody AuthorityRequest request) {
        try {
            return successResponse(libraryService.createAuthority(request));
        } catch (Exception e) {
            return failureResponse("Failed to create authority", e.getMessage());
        }
    }

    @PutMapping("/authorities/{uuid}")
    public Object updateAuthority(@PathVariable UUID uuid, @RequestBody AuthorityRequest request) {
        try {
            return successResponse(libraryService.updateAuthority(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update authority", e.getMessage());
        }
    }

    @GetMapping("/jurisdictions")
    public Object listJurisdictions(
            @RequestParam(required = false) String emirate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean verifiedOnly) {
        try {
            return successResponse(libraryService.listJurisdictions(emirate, search, verifiedOnly));
        } catch (Exception e) {
            return failureResponse("Failed to list jurisdictions", e.getMessage());
        }
    }

    @PostMapping("/jurisdictions")
    public Object createJurisdiction(@RequestBody JurisdictionRequest request) {
        try {
            return successResponse(libraryService.createJurisdiction(request));
        } catch (Exception e) {
            return failureResponse("Failed to create jurisdiction", e.getMessage());
        }
    }

    @PutMapping("/jurisdictions/{uuid}")
    public Object updateJurisdiction(@PathVariable UUID uuid, @RequestBody JurisdictionRequest request) {
        try {
            return successResponse(libraryService.updateJurisdiction(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update jurisdiction", e.getMessage());
        }
    }

    @GetMapping("/permit-types")
    public Object listPermitTypes(
            @RequestParam(required = false) String authorityCode,
            @RequestParam(required = false) String search) {
        try {
            return successResponse(libraryService.listPermitTypes(authorityCode, search));
        } catch (Exception e) {
            return failureResponse("Failed to list permit types", e.getMessage());
        }
    }

    @PostMapping("/permit-types")
    public Object createPermitType(@RequestBody PermitTypeRequest request) {
        try {
            return successResponse(libraryService.createPermitType(request));
        } catch (Exception e) {
            return failureResponse("Failed to create permit type", e.getMessage());
        }
    }

    @PutMapping("/permit-types/{uuid}")
    public Object updatePermitType(@PathVariable UUID uuid, @RequestBody PermitTypeRequest request) {
        try {
            return successResponse(libraryService.updatePermitType(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update permit type", e.getMessage());
        }
    }

    @GetMapping("/document-types")
    public Object listDocumentTypes(@RequestParam(required = false) String category) {
        try {
            return successResponse(libraryService.listDocumentTypes(category));
        } catch (Exception e) {
            return failureResponse("Failed to list document types", e.getMessage());
        }
    }

    @GetMapping("/compliance/company")
    public Object listCompanyCompliance() {
        try {
            return successResponse(libraryService.listCompanyCompliance());
        } catch (Exception e) {
            return failureResponse("Failed to load company compliance", e.getMessage());
        }
    }

    @PutMapping("/compliance/company")
    public Object upsertCompanyCompliance(@RequestBody CompanyComplianceRequest request) {
        try {
            return successResponse(libraryService.upsertCompanyCompliance(request));
        } catch (Exception e) {
            return failureResponse("Failed to save compliance document", e.getMessage());
        }
    }

    @GetMapping("/seed/status")
    public Object seedStatus() {
        try {
            return successResponse(java.util.Map.of(
                    "seedFileFound", seedFileLocator.exists(),
                    "location", seedFileLocator.describeLocation() == null ? "" : seedFileLocator.describeLocation()));
        } catch (Exception e) {
            return failureResponse("Failed to check seed status", e.getMessage());
        }
    }

    /** Imports from the seed file already on disk in the repo's docs folder. */
    @PostMapping("/seed/import")
    public Object importSeed() {
        try {
            return successResponse("Seed imported", seedImportService.importFromConfiguredFile());
        } catch (Exception e) {
            return failureResponse("Failed to import seed", e.getMessage());
        }
    }

    /** Imports an uploaded copy of the companion JSON. */
    @PostMapping(value = "/seed/import-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object importSeedUpload(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Seed imported",
                    seedImportService.importFromJson(file.getBytes(), file.getOriginalFilename()));
        } catch (Exception e) {
            return failureResponse("Failed to import uploaded seed", e.getMessage());
        }
    }
}
