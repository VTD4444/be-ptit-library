package vn.edu.ptit.library;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.entity.UserProfile;

import java.util.UUID;

class SmartLibraryApplicationTests {

    @Test
    @DisplayName("Kiem tra khoi tao Entity User va UserProfile voi Persistable")
    void testUserAndProfileInstantiation() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .userCode("B22DCPT058")
                .fullName("Dinh Vu Trung Duc")
                .email("duc@ptit.edu.vn")
                .role("READER")
                .build();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .user(user)
                .personalizationEnabled(true)
                .build();

        Assertions.assertNotNull(user);
        Assertions.assertEquals("B22DCPT058", user.getUserCode());
        Assertions.assertTrue(profile.isNew());
    }

    @Test
    @DisplayName("Kiem tra Composite Key khoi tao mac dinh tranh NPE")
    void testCompositeKeyInitialization() {
        DocumentAuthor docAuthor = new DocumentAuthor();
        Assertions.assertNotNull(docAuthor.getId());
        Assertions.assertInstanceOf(DocumentAuthorId.class, docAuthor.getId());

        Document doc = Document.builder()
                .title("Giao trinh Mang may tinh PTIT")
                .build();
        Assertions.assertEquals("Giao trinh Mang may tinh PTIT", doc.getTitle());
    }

    @Test
    @DisplayName("Kiem tra ApiResponse chuan")
    void testApiResponse() {
        ApiResponse<String> response = ApiResponse.success("Hello PTIT Smart Library");
        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals("Hello PTIT Smart Library", response.getData());
    }

    @Test
    @DisplayName("Kiem tra ContentSearchRequest mac dinh")
    void testContentSearchRequest() {
        ContentSearchRequest req = ContentSearchRequest.builder()
                .query("hoc may va xu ly ngon ngu tu nhien")
                .build();
        Assertions.assertEquals(10, req.getLimit());
        Assertions.assertEquals("HYBRID", req.getSearchType());
    }
}
