package vn.edu.ptit.library.catalog.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;

import java.io.IOException;
import java.util.UUID;

public interface DigitalCopyService {

    DigitalCopy uploadPdf(UUID documentId, MultipartFile file, String version) throws IOException;

    Resource downloadPdf(UUID digitalCopyId);

    DigitalCopy getById(UUID digitalCopyId);
}
