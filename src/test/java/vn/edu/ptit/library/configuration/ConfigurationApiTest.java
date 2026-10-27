package vn.edu.ptit.library.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import vn.edu.ptit.library.circulation.controller.*;
import vn.edu.ptit.library.circulation.dto.*;
import vn.edu.ptit.library.circulation.service.CirculationConfigurationService;
import vn.edu.ptit.library.search.controller.SearchConfigAdminController;
import vn.edu.ptit.library.search.dto.SearchConfigResponse;
import vn.edu.ptit.library.search.service.SearchConfigurationService;
import vn.edu.ptit.library.security.*;
import vn.edu.ptit.library.common.exception.*;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PolicyAdminController.class, PenaltyTypeAdminController.class, SearchConfigAdminController.class,
        vn.edu.ptit.library.catalog.controller.CatalogController.class,
        vn.edu.ptit.library.user.controller.AuthController.class, CirculationController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ConfigurationApiTest {
    @Autowired MockMvc mvc;
    @MockBean CirculationConfigurationService circulation;
    @MockBean SearchConfigurationService search;
    @MockBean JwtTokenProvider tokens;
    @MockBean vn.edu.ptit.library.catalog.service.CatalogService catalog;
    @MockBean vn.edu.ptit.library.user.service.AuthService auth;
    @MockBean vn.edu.ptit.library.circulation.service.CirculationService loans;

    private static final String POLICY = """
            {"userType":"STUDENT","docType":"BOOK","loanDays":14,"maxLoans":5,"maxRenewals":2,"holdExpiryDays":3}
            """;
    private static final String PENALTY = """
            {"id":1,"code":"LATE","name":"Late fee","unitName":"day","unitPrice":5000}
            """;

    @ParameterizedTest
    @CsvSource({
            "GET,/api/admin/policies,LIBRARIAN,200", "GET,/api/admin/policies,ADMIN,200",
            "GET,/api/admin/penalty-types,LIBRARIAN,200", "GET,/api/admin/penalty-types,ADMIN,200",
            "GET,/api/admin/search-configs,LIBRARIAN,403", "GET,/api/admin/search-configs,ADMIN,200",
            "PUT,/api/admin/policies/1,LIBRARIAN,403", "PUT,/api/admin/policies/1,ADMIN,200",
            "POST,/api/admin/penalty-types,LIBRARIAN,403", "POST,/api/admin/penalty-types,ADMIN,200",
            "PUT,/api/admin/penalty-types,LIBRARIAN,403", "PUT,/api/admin/penalty-types,ADMIN,200",
            "PUT,/api/admin/search-configs/1/active,LIBRARIAN,403", "PUT,/api/admin/search-configs/1/active,ADMIN,200",
            "GET,/api/admin/policies,READER,403", "GET,/api/admin/penalty-types,READER,403",
            "GET,/api/admin/search-configs,READER,403", "PUT,/api/admin/policies/1,READER,403",
            "POST,/api/admin/penalty-types,READER,403", "PUT,/api/admin/penalty-types,READER,403",
            "PUT,/api/admin/search-configs/1/active,READER,403"})
    void roleMatrix(String method, String path, String role, int status) throws Exception {
        var request = request(org.springframework.http.HttpMethod.valueOf(method), path)
                .with(user("test").roles(role)).contentType(MediaType.APPLICATION_JSON)
                .content(path.contains("policies") ? POLICY : PENALTY);
        mvc.perform(request).andExpect(status().is(status));
        if (status == 403) verifyNoInteractions(circulation, search);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/admin/policies", "GET,/api/admin/penalty-types", "GET,/api/admin/search-configs",
            "PUT,/api/admin/policies/1", "POST,/api/admin/penalty-types", "PUT,/api/admin/penalty-types",
            "PUT,/api/admin/search-configs/1/active"})
    void anonymousCannotAccess(String method, String path) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(POLICY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1100));
        verifyNoInteractions(circulation, search);
    }

    @Test
    void listsReturnExpectedDtosWithoutAuditFields() throws Exception {
        when(circulation.getPolicies()).thenReturn(List.of(new PolicyResponse(1L,"STUDENT","BOOK",14,5,2,3)));
        when(circulation.getPenaltyTypes()).thenReturn(List.of(new PenaltyTypeResponse(1L,"LATE","Late fee","day",5000L)));
        when(search.getConfigurations()).thenReturn(List.of(new SearchConfigResponse(1L,512,64,"fixed","model",768,true)));
        mvc.perform(get("/api/admin/policies").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].loanDays").value(14)).andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
        mvc.perform(get("/api/admin/penalty-types").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].unitPrice").value(5000));
        mvc.perform(get("/api/admin/search-configs").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].isActive").value(true));
    }

    @Test
    void writesBindOnlyBusinessFieldsAndReturnDtos() throws Exception {
        var policyRequest = new PolicyUpdateRequest(vn.edu.ptit.library.common.enums.UserType.STUDENT, "BOOK", 14, 5, 2, 3);
        when(circulation.updatePolicy(1L, policyRequest))
                .thenReturn(new PolicyResponse(1L, "STUDENT", "BOOK", 14, 5, 2, 3));
        when(circulation.createPenaltyType(new PenaltyTypeCreateRequest("LATE", "Late fee", "day", 5000L)))
                .thenReturn(new PenaltyTypeResponse(2L, "LATE", "Late fee", "day", 5000L));
        when(circulation.updatePenaltyType(new PenaltyTypeUpdateRequest(1L, "LATE", "Late fee", "day", 5000L)))
                .thenReturn(new PenaltyTypeResponse(1L, "LATE", "Late fee", "day", 5000L));
        when(search.activate(1L)).thenReturn(new SearchConfigResponse(1L, 512, 64, "fixed", "model", 768, true));
        mvc.perform(put("/api/admin/policies/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(POLICY))
                .andExpect(jsonPath("$.data.userType").value("STUDENT")).andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(post("/api/admin/penalty-types").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(PENALTY.replace("LATE", " LATE ")))
                .andExpect(jsonPath("$.data.id").value(2)).andExpect(jsonPath("$.data.unitPrice").value(5000));
        mvc.perform(put("/api/admin/penalty-types").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(PENALTY))
                .andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(put("/api/admin/search-configs/1/active").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.data.isActive").value(true));
        verify(circulation).updatePolicy(1L, policyRequest);
        verify(circulation).createPenaltyType(new PenaltyTypeCreateRequest("LATE", "Late fee", "day", 5000L));
        verify(circulation).updatePenaltyType(new PenaltyTypeUpdateRequest(1L, "LATE", "Late fee", "day", 5000L));
        verify(search).activate(1L);
    }

    @ParameterizedTest
    @CsvSource({"loanDays,0", "maxLoans,-1", "maxRenewals,-1", "holdExpiryDays,-1", "loanDays,null"})
    void invalidPolicyNumbers(String field, String value) throws Exception {
        String body = POLICY.replaceAll("\"" + field + "\":\\d+", "\"" + field + "\":" + value);
        mvc.perform(put("/api/admin/policies/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(circulation);
    }

    @ParameterizedTest
    @CsvSource({"/api/admin/policies/0", "/api/admin/policies/abc", "/api/admin/search-configs/-1/active"})
    void invalidPathIds(String path) throws Exception {
        mvc.perform(put(path).with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(POLICY)).andExpect(status().isBadRequest());
        verifyNoInteractions(circulation, search);
    }

    @Test
    void missingFieldsInvalidEnumAndMalformedJsonAreBadRequests() throws Exception {
        for (String body : List.of("{}", "{", POLICY.replace("STUDENT", "UNKNOWN"), POLICY.replace("BOOK", "   "))) {
            mvc.perform(put("/api/admin/policies/1").with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(circulation);
    }

    @Test
    void penaltyValidationRejectsNegativePriceBlankCodeAndMissingUpdateId() throws Exception {
        for (String body : List.of(PENALTY.replace("5000", "-1"), PENALTY.replace("LATE", "   "),
                PENALTY.replace("\"id\":1,", ""), PENALTY.replace("\"id\":1", "\"id\":0"))) {
            mvc.perform(put("/api/admin/penalty-types").with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/penalty-types").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(PENALTY.replace("5000", "-1")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(circulation);
    }

    @Test
    void businessErrorsPreserveStatusAndDoNotExposeInternalDetails() throws Exception {
        when(circulation.updatePolicy(eq(99L), any())).thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        when(circulation.createPenaltyType(any())).thenThrow(new AppException(ErrorCode.DUPLICATE_RESOURCE));
        when(search.activate(99L)).thenThrow(new AppException(ErrorCode.SEARCH_CONFIG_NOT_FOUND));
        mvc.perform(put("/api/admin/policies/99").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(POLICY)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/penalty-types").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(PENALTY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1003)).andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(put("/api/admin/search-configs/99/active").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1401));
    }

    @Test
    void existingRoutesAndSecurityRemainAvailable() throws Exception {
        mvc.perform(get("/api/v1/catalog/documents")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"test-only\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/circulation/borrow").with(user("reader").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"00000000-0000-0000-0000-000000000001\",\"physicalCopyId\":\"00000000-0000-0000-0000-000000000002\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(loans);
    }
}
