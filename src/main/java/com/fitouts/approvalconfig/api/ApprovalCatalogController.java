package com.fitouts.approvalconfig.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.approvalconfig.application.ApprovalCatalogService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApprovalCatalogController extends BaseController {

    private final ApprovalCatalogService catalogService;

    @GetMapping("/approvals-catalog")
    public ResponseEntity<?> loadCatalog() {
        try {
            return successResponse(catalogService.loadCatalog());
        } catch (Exception e) {
            String detail = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage()
                    : e.getMessage();
            return failureResponse("Failed to load approvals catalog", detail);
        }
    }

    @GetMapping("/authorities")
    public ResponseEntity<?> listAuthorities() {
        try {
            return successResponse(catalogService.listAuthorities());
        } catch (Exception e) {
            return failureResponse("Failed to fetch authorities", e.getMessage());
        }
    }

    @PostMapping("/authorities")
    public ResponseEntity<?> createAuthority(@RequestBody AuthorityRequest request) {
        try {
            return successResponse("Authority created", catalogService.createAuthority(request));
        } catch (Exception e) {
            return failureResponse("Failed to create authority", e.getMessage());
        }
    }

    @PutMapping("/authorities/{id}")
    public ResponseEntity<?> updateAuthority(@PathVariable UUID id, @RequestBody AuthorityRequest request) {
        try {
            return successResponse("Authority updated", catalogService.updateAuthority(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update authority", e.getMessage());
        }
    }

    @DeleteMapping("/authorities/{id}")
    public ResponseEntity<?> deleteAuthority(@PathVariable UUID id) {
        try {
            catalogService.deleteAuthority(id);
            return successResponse("Authority deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete authority", e.getMessage());
        }
    }

    @GetMapping("/permit-types")
    public ResponseEntity<?> listPermitTypes() {
        try {
            return successResponse(catalogService.listPermitTypes());
        } catch (Exception e) {
            return failureResponse("Failed to fetch permit types", e.getMessage());
        }
    }

    @PostMapping("/permit-types")
    public ResponseEntity<?> createPermitType(@RequestBody PermitTypeRequest request) {
        try {
            return successResponse("Permit type created", catalogService.createPermitType(request));
        } catch (Exception e) {
            return failureResponse("Failed to create permit type", e.getMessage());
        }
    }

    @PutMapping("/permit-types/{id}")
    public ResponseEntity<?> updatePermitType(@PathVariable UUID id, @RequestBody PermitTypeRequest request) {
        try {
            return successResponse("Permit type updated", catalogService.updatePermitType(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update permit type", e.getMessage());
        }
    }

    @DeleteMapping("/permit-types/{id}")
    public ResponseEntity<?> deletePermitType(@PathVariable UUID id) {
        try {
            catalogService.deletePermitType(id);
            return successResponse("Permit type deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete permit type", e.getMessage());
        }
    }

    @GetMapping("/document-types")
    public ResponseEntity<?> listDocumentTypes() {
        try {
            return successResponse(catalogService.listDocumentTypes());
        } catch (Exception e) {
            return failureResponse("Failed to fetch document types", e.getMessage());
        }
    }

    @PostMapping("/document-types")
    public ResponseEntity<?> createDocumentType(@RequestBody DocumentTypeRequest request) {
        try {
            return successResponse("Document type created", catalogService.createDocumentType(request));
        } catch (Exception e) {
            return failureResponse("Failed to create document type", e.getMessage());
        }
    }

    @PutMapping("/document-types/{id}")
    public ResponseEntity<?> updateDocumentType(@PathVariable UUID id, @RequestBody DocumentTypeRequest request) {
        try {
            return successResponse("Document type updated", catalogService.updateDocumentType(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update document type", e.getMessage());
        }
    }

    @DeleteMapping("/document-types/{id}")
    public ResponseEntity<?> deleteDocumentType(@PathVariable UUID id) {
        try {
            catalogService.deleteDocumentType(id);
            return successResponse("Document type deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete document type", e.getMessage());
        }
    }

    @GetMapping("/scope-tags")
    public ResponseEntity<?> listScopeTags() {
        try {
            return successResponse(catalogService.listScopeTags());
        } catch (Exception e) {
            return failureResponse("Failed to fetch scope tags", e.getMessage());
        }
    }

    @PostMapping("/scope-tags")
    public ResponseEntity<?> createScopeTag(@RequestBody ScopeTagRequest request) {
        try {
            return successResponse("Scope tag created", catalogService.createScopeTag(request));
        } catch (Exception e) {
            return failureResponse("Failed to create scope tag", e.getMessage());
        }
    }

    @PutMapping("/scope-tags/{id}")
    public ResponseEntity<?> updateScopeTag(@PathVariable UUID id, @RequestBody ScopeTagRequest request) {
        try {
            return successResponse("Scope tag updated", catalogService.updateScopeTag(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update scope tag", e.getMessage());
        }
    }

    @DeleteMapping("/scope-tags/{id}")
    public ResponseEntity<?> deleteScopeTag(@PathVariable UUID id) {
        try {
            catalogService.deleteScopeTag(id);
            return successResponse("Scope tag deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete scope tag", e.getMessage());
        }
    }

    @GetMapping("/property-types")
    public ResponseEntity<?> listPropertyTypes() {
        try {
            return successResponse(catalogService.listPropertyTypes());
        } catch (Exception e) {
            return failureResponse("Failed to fetch property types", e.getMessage());
        }
    }

    @PostMapping("/property-types")
    public ResponseEntity<?> createPropertyType(@RequestBody CatalogItemRequest request) {
        try {
            return successResponse("Property type created", catalogService.createPropertyType(request));
        } catch (Exception e) {
            return failureResponse("Failed to create property type", e.getMessage());
        }
    }

    @PutMapping("/property-types/{id}")
    public ResponseEntity<?> updatePropertyType(@PathVariable UUID id, @RequestBody CatalogItemRequest request) {
        try {
            return successResponse("Property type updated", catalogService.updatePropertyType(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update property type", e.getMessage());
        }
    }

    @DeleteMapping("/property-types/{id}")
    public ResponseEntity<?> deletePropertyType(@PathVariable UUID id) {
        try {
            catalogService.deletePropertyType(id);
            return successResponse("Property type deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete property type", e.getMessage());
        }
    }

    @GetMapping("/project-natures")
    public ResponseEntity<?> listProjectNatures() {
        try {
            return successResponse(catalogService.listProjectNatures());
        } catch (Exception e) {
            return failureResponse("Failed to fetch project natures", e.getMessage());
        }
    }

    @PostMapping("/project-natures")
    public ResponseEntity<?> createProjectNature(@RequestBody CatalogItemRequest request) {
        try {
            return successResponse("Project nature created", catalogService.createProjectNature(request));
        } catch (Exception e) {
            return failureResponse("Failed to create project nature", e.getMessage());
        }
    }

    @PutMapping("/project-natures/{id}")
    public ResponseEntity<?> updateProjectNature(@PathVariable UUID id, @RequestBody CatalogItemRequest request) {
        try {
            return successResponse("Project nature updated", catalogService.updateProjectNature(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update project nature", e.getMessage());
        }
    }

    @DeleteMapping("/project-natures/{id}")
    public ResponseEntity<?> deleteProjectNature(@PathVariable UUID id) {
        try {
            catalogService.deleteProjectNature(id);
            return successResponse("Project nature deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete project nature", e.getMessage());
        }
    }

    @GetMapping("/company-registrations")
    public ResponseEntity<?> listCompanyRegistrations() {
        try {
            return successResponse(catalogService.listCompanyRegistrations());
        } catch (Exception e) {
            return failureResponse("Failed to fetch company registrations", e.getMessage());
        }
    }

    @PostMapping("/company-registrations")
    public ResponseEntity<?> createCompanyRegistration(@RequestBody CompanyRegistrationRequest request) {
        try {
            return successResponse("Company registration created", catalogService.createCompanyRegistration(request));
        } catch (Exception e) {
            return failureResponse("Failed to create company registration", e.getMessage());
        }
    }

    @PutMapping("/company-registrations/{id}")
    public ResponseEntity<?> updateCompanyRegistration(@PathVariable UUID id, @RequestBody CompanyRegistrationRequest request) {
        try {
            return successResponse("Company registration updated", catalogService.updateCompanyRegistration(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update company registration", e.getMessage());
        }
    }

    @DeleteMapping("/company-registrations/{id}")
    public ResponseEntity<?> deleteCompanyRegistration(@PathVariable UUID id) {
        try {
            catalogService.deleteCompanyRegistration(id);
            return successResponse("Company registration deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete company registration", e.getMessage());
        }
    }
}
