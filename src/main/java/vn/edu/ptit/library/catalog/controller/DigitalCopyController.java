package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;
import vn.edu.ptit.library.catalog.service.DigitalCopyService;
import vn.edu.ptit.library.common.dto.ApiResponse;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/digital")
@RequiredArgsConstructor
@Tag(name = "Digital Copies", description = "Quan ly va tai len ban so PDF")
public class DigitalCopyController {

    private final DigitalCopyService digitalCopyService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Tai len tep PDF ban so cho tai lieu (Can bo / Admin)")
    public ApiResponse<DigitalCopy> uploadPdf(
            @RequestParam("documentId") UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "version", defaultValue = "1.0") String version) throws IOException {
        return ApiResponse.success("Tai len tep PDF thanh cong", digitalCopyService.uploadPdf(documentId, file, version));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Tai xuong tep PDF ban so")
    public ResponseEntity<Resource> downloadPdf(@PathVariable("id") UUID id) {
        Resource resource = digitalCopyService.downloadPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
