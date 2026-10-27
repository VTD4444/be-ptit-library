package vn.edu.ptit.library.circulation.service;

import java.util.List;
import vn.edu.ptit.library.circulation.dto.*;

public interface CirculationConfigurationService {
    List<PolicyResponse> getPolicies();
    PolicyResponse updatePolicy(Long id, PolicyUpdateRequest request);
    List<PenaltyTypeResponse> getPenaltyTypes();
    PenaltyTypeResponse createPenaltyType(PenaltyTypeCreateRequest request);
    PenaltyTypeResponse updatePenaltyType(PenaltyTypeUpdateRequest request);
}
