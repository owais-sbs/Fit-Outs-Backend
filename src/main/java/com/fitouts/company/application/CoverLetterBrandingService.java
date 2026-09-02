package com.fitouts.company.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.company.api.CoverLetterBrandingResponse;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.PortalAccessHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CoverLetterBrandingService {

    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorageService;
    private final PortalAccessHelper portalAccess;

    @Transactional(readOnly = true)
    public CoverLetterBrandingResponse get() {
        portalAccess.requireStaff();
        return toResponse(currentCompany());
    }

    @Transactional
    public CoverLetterBrandingResponse uploadStamp(MultipartFile file) {
        return upload(file, true);
    }

    @Transactional
    public CoverLetterBrandingResponse uploadSignature(MultipartFile file) {
        return upload(file, false);
    }

    @Transactional
    public CoverLetterBrandingResponse deleteStamp() {
        return delete(true);
    }

    @Transactional
    public CoverLetterBrandingResponse deleteSignature() {
        return delete(false);
    }

    private CoverLetterBrandingResponse upload(MultipartFile file, boolean stamp) {
        portalAccess.requireStaff();
        requireImage(file);
        Company company = currentCompany();
        String previous = stamp ? company.getStampImagePath() : company.getSignatureImagePath();
        String path = fileStorageService.store(file, "cover-letter/" + company.getUuid());
        if (stamp) {
            company.setStampImagePath(path);
        } else {
            company.setSignatureImagePath(path);
        }
        companyRepository.save(company);
        fileStorageService.deleteIfExists(previous);
        return toResponse(company);
    }

    private CoverLetterBrandingResponse delete(boolean stamp) {
        portalAccess.requireStaff();
        Company company = currentCompany();
        if (stamp) {
            fileStorageService.deleteIfExists(company.getStampImagePath());
            company.setStampImagePath(null);
        } else {
            fileStorageService.deleteIfExists(company.getSignatureImagePath());
            company.setSignatureImagePath(null);
        }
        companyRepository.save(company);
        return toResponse(company);
    }

    private Company currentCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    private CoverLetterBrandingResponse toResponse(Company company) {
        return CoverLetterBrandingResponse.builder()
                .stampUrl(SiteVisitEstimateMapper.toFileUrl(company.getStampImagePath()))
                .signatureUrl(SiteVisitEstimateMapper.toFileUrl(company.getSignatureImagePath()))
                .build();
    }

    public static void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BadRequestException("Upload a PNG or JPG image");
        }
    }
}
