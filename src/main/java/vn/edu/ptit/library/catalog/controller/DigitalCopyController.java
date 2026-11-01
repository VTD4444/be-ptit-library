package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.dto.DigitalCopyUploadResponse;
import vn.edu.ptit.library.catalog.dto.ProcessingStatusResponse;
import vn.edu.ptit.library.catalog.service.DigitalCopyService;
import vn.edu.ptit.library.common.dto.ApiResponse;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Digital Copies", description = "Quan ly, doc truc tuyen va theo doi boc tach ban so PDF")
public class DigitalCopyController {

    private final DigitalCopyService digitalCopyService;

    @PostMapping(value = "/api/admin/documents/{id}/digital-copies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Tai len tep PDF ban so cho dau sach va khoi chay tien trinh boc tach ngam (Can bo / Admin)")
    public ApiResponse<DigitalCopyUploadResponse> uploadPdf(
            @PathVariable("id") UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "version", defaultValue = "1.0") String version) throws IOException {
        return ApiResponse.success("Tai len tep PDF thanh cong, dang boc tach du lieu ngam",
                digitalCopyService.uploadPdf(documentId, file, version));
    }

    @GetMapping("/api/documents/{id}/read")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Doc truc tuyen ban so hien hanh cua dau sach (stream PDF, ho tro HTTP Range)")
    public ResponseEntity<Resource> readOnline(@PathVariable("id") UUID documentId) {
        Resource resource = digitalCopyService.readCurrentPdf(documentId);
        // Tra ve ResponseEntity<Resource> de Spring tu xu ly header Range (PDF.js tai tung phan)
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + documentId + ".pdf\"")
                .cacheControl(CacheControl.noStore())
                .body(resource);
    }

    @GetMapping("/api/admin/digital-copies/processing-status")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Kiem tra tien trinh boc tach PDF theo ID job (Cho, Dang chay, Xong, Loi)")
    public ApiResponse<ProcessingStatusResponse> getProcessingStatus(@RequestParam("jobId") Long jobId) {
        return ApiResponse.success(digitalCopyService.getProcessingStatus(jobId));
    }

    @GetMapping("/api/catalog/digital/{id}/download")
    @Operation(summary = "Tai xuong tep PDF ban so")
    public ResponseEntity<Resource> downloadPdf(@PathVariable("id") UUID id) {
        Resource resource = digitalCopyService.downloadPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
