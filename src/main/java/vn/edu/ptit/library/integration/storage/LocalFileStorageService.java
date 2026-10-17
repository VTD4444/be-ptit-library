package vn.edu.ptit.library.integration.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements StorageService {

    private final Path rootLocation;

    public LocalFileStorageService(@Value("${app.storage.upload-dir:./uploads/digital-copies}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize storage directory at {}", this.rootLocation, e);
        }
    }

    @Override
    public String storePdf(MultipartFile file, String subDirectory) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf");
        String extension = "";
        int extIndex = originalFilename.lastIndexOf('.');
        if (extIndex > 0) {
            extension = originalFilename.substring(extIndex);
        }

        String storedFileName = UUID.randomUUID() + extension;
        Path targetDir = subDirectory != null ? this.rootLocation.resolve(subDirectory) : this.rootLocation;
        Files.createDirectories(targetDir);

        Path targetLocation = targetDir.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        Path relativePath = this.rootLocation.relativize(targetLocation);
        return relativePath.toString().replace('\\', '/');
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            Path file = this.rootLocation.resolve(relativePath).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tệp hoặc tệp không đọc được");
        } catch (MalformedURLException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Đường dẫn tệp không hợp lệ");
        }
    }

    @Override
    public String calculateSha256(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", e);
        }
    }

    @Override
    public boolean deleteFile(String relativePath) {
        try {
            Path file = this.rootLocation.resolve(relativePath).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete file at {}: {}", relativePath, e.getMessage());
            return false;
        }
    }
}
