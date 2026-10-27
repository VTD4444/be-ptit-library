package vn.edu.ptit.library.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import vn.edu.ptit.library.circulation.dto.*;
import vn.edu.ptit.library.circulation.entity.*;
import vn.edu.ptit.library.circulation.repository.*;
import vn.edu.ptit.library.circulation.service.impl.CirculationConfigurationServiceImpl;
import vn.edu.ptit.library.common.enums.UserType;
import vn.edu.ptit.library.common.exception.*;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;
import vn.edu.ptit.library.search.repository.*;
import vn.edu.ptit.library.search.service.impl.SearchConfigurationServiceImpl;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationServicesTest {
    private final LoanPolicyRepository policies = mock(LoanPolicyRepository.class);
    private final PenaltyTypeRepository penalties = mock(PenaltyTypeRepository.class);
    private final CirculationConfigurationServiceImpl circulation =
            new CirculationConfigurationServiceImpl(policies, penalties);
    private final SearchVectorConfigRepository configs = mock(SearchVectorConfigRepository.class);
    private final SearchConfigActivationLock lock = mock(SearchConfigActivationLock.class);
    private final SearchConfigurationServiceImpl search = new SearchConfigurationServiceImpl(configs, lock);

    @Test
    void listsMapOnlyBusinessFields() {
        when(policies.findAll(Sort.by("id"))).thenReturn(List.of(LoanPolicy.builder().id(1L).loanDays(14).build()));
        when(penalties.findAll(Sort.by("id"))).thenReturn(List.of(PenaltyType.builder().id(2L).unitPrice(5000L).build()));
        when(configs.findAll(Sort.by("id"))).thenReturn(List.of(SearchVectorConfig.builder().id(3L).embeddingModel("model").build()));
        assertThat(circulation.getPolicies().get(0).loanDays()).isEqualTo(14);
        assertThat(circulation.getPenaltyTypes().get(0).unitPrice()).isEqualTo(5000L);
        assertThat(search.getConfigurations().get(0).embeddingModel()).isEqualTo("model");
    }

    @Test
    void updatesAllPolicyFieldsAndPreservesId() {
        var policy = LoanPolicy.builder().id(1L).build();
        when(policies.findById(1L)).thenReturn(Optional.of(policy));
        when(policies.save(policy)).thenReturn(policy);
        var result = circulation.updatePolicy(1L, new PolicyUpdateRequest(UserType.STUDENT, " BOOK ", 14, 5, 2, 3));
        assertThat(result).isEqualTo(new PolicyResponse(1L, "STUDENT", "BOOK", 14, 5, 2, 3));
    }

    @Test
    void missingPolicyOrPenaltyDoesNotWrite() {
        assertThatThrownBy(() -> circulation.updatePolicy(9L,
                new PolicyUpdateRequest(UserType.STUDENT, "BOOK", 14, 5, 2, 3)))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> circulation.updatePenaltyType(new PenaltyTypeUpdateRequest(9L, "A", "Fine", "day", 1L)))
                .isInstanceOf(AppException.class);
        verify(policies, never()).save(any());
        verify(penalties, never()).save(any());
    }

    @Test
    void createsTrimmedPenaltyWithoutChangingCodeCase() {
        when(penalties.save(any())).thenAnswer(call -> {
            PenaltyType entity = call.getArgument(0);
            entity.setId(4L);
            return entity;
        });
        var result = circulation.createPenaltyType(new PenaltyTypeCreateRequest(" Late ", " Late fee ", " day ", 0L));
        assertThat(result).isEqualTo(new PenaltyTypeResponse(4L, "Late", "Late fee", "day", 0L));
    }

    @Test
    void duplicatePenaltyOnCreateOrUpdateIsConflict() {
        when(penalties.findByCode("LATE")).thenReturn(Optional.of(PenaltyType.builder().id(1L).build()));
        when(penalties.findById(2L)).thenReturn(Optional.of(PenaltyType.builder().id(2L).build()));
        assertThatThrownBy(() -> circulation.createPenaltyType(new PenaltyTypeCreateRequest("LATE", "Late", "day", 1L)))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        assertThatThrownBy(() -> circulation.updatePenaltyType(new PenaltyTypeUpdateRequest(2L, "LATE", "Late", "day", 1L)))
                .isInstanceOf(AppException.class);
        verify(penalties, never()).save(any());
    }

    @Test
    void updatingSamePenaltyCodeIsAllowed() {
        var entity = PenaltyType.builder().id(1L).code("LATE").build();
        when(penalties.findById(1L)).thenReturn(Optional.of(entity));
        when(penalties.findByCode("LATE")).thenReturn(Optional.of(entity));
        when(penalties.save(entity)).thenReturn(entity);
        assertThat(circulation.updatePenaltyType(new PenaltyTypeUpdateRequest(1L, "LATE", "Late", "day", 500L)))
                .isEqualTo(new PenaltyTypeResponse(1L, "LATE", "Late", "day", 500L));
    }

    @Test
    void activationLocksBeforeReadingAndRepairsMultipleActiveFlags() {
        var first = SearchVectorConfig.builder().id(1L).isActive(true).embeddingModel("first").build();
        var second = SearchVectorConfig.builder().id(2L).isActive(true).embeddingModel("second").build();
        var rows = List.of(first, second);
        when(configs.findAll(Sort.by("id"))).thenReturn(rows);
        assertThat(search.activate(2L).isActive()).isTrue();
        assertThat(first.getIsActive()).isFalse();
        assertThat(second.getEmbeddingModel()).isEqualTo("second");
        search.activate(2L);
        assertThat(rows.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count()).isEqualTo(1);
        var order = inOrder(lock, configs);
        order.verify(lock).acquire();
        order.verify(configs).findAll(Sort.by("id"));
        order.verify(configs).saveAllAndFlush(rows);
    }

    @Test
    void missingConfigDoesNotDeactivateExistingConfig() {
        var active = SearchVectorConfig.builder().id(1L).isActive(true).build();
        when(configs.findAll(Sort.by("id"))).thenReturn(List.of(active));
        assertThatThrownBy(() -> search.activate(9L)).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SEARCH_CONFIG_NOT_FOUND));
        assertThat(active.getIsActive()).isTrue();
        verify(configs, never()).saveAllAndFlush(any());
    }

    @Test
    void writeFailurePropagatesInsteadOfReturningSuccess() {
        when(configs.findAll(Sort.by("id"))).thenReturn(List.of(SearchVectorConfig.builder().id(1L).isActive(false).build()));
        doThrow(new IllegalStateException("test write failure")).when(configs).saveAllAndFlush(any());
        assertThatThrownBy(() -> search.activate(1L)).isInstanceOf(IllegalStateException.class);
        // Actual transaction rollback is tested only by the opt-in PostgreSQL tests.
    }
}
