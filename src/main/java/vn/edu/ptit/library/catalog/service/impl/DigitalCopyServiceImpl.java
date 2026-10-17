package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.service.DigitalCopyService;
import vn.edu.ptit.library.common.enums.DigitalProcessingStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.integration.storage.StorageService;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalCopyServiceImpl implements DigitalCopyService {

    private final DigitalCopyRepository digitalCopyRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public DigitalCopy uploadPdf(UUID documentId, MultipartFile file, String version) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tệp tải lên phải là định dạng PDF không rỗng");
        }

        String hash = storageService.calculateSha256(file);
        String relativePath = storageService.storePdf(file, "docs/" + documentId);

        // Dat cac ban cu ve isCurrent = false
        digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(documentId).ifPresent(oldCopy -> {
            oldCopy.setIsCurrent(false);
            digitalCopyRepository.save(oldCopy);
        });

        User currentUser = SecurityUtils.getCurrentUserDetails()
                .flatMap(u -> userRepository.findById(u.getId()))
                .orElse(null);

        DigitalCopy digitalCopy = DigitalCopy.builder()
                .document(document)
                .version(version != null && !version.isBlank() ? version : "1.0")
                .isCurrent(true)
                .processingStatus(DigitalProcessingStatus.PENDING.name())
                .filePath(relativePath)
                .fileHash(hash)
                .fileSize(file.getSize())
                .uploadedBy(currentUser)
                .build();

        return digitalCopyRepository.save(digitalCopy);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadPdf(UUID digitalCopyId) {
        DigitalCopy digitalCopy = getById(digitalCopyId);
        return storageService.loadAsResource(digitalCopy.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public DigitalCopy getById(UUID digitalCopyId) {
        return digitalCopyRepository.findById(digitalCopyId)
                .orElseThrow(() -> new AppException(ErrorCode.DIGITAL_COPY_NOT_FOUND));
    }
}
