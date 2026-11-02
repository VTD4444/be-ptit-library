package vn.edu.ptit.library.integration.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface StorageService {

    String storePdf(MultipartFile file, String subDirectory) throws IOException;

    Resource loadAsResource(String relativePath);

    Path resolveAbsolutePath(String relativePath);

    String calculateSha256(MultipartFile file) throws IOException;

    boolean deleteFile(String relativePath);
}
