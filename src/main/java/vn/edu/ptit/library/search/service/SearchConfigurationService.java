package vn.edu.ptit.library.search.service;

import java.util.List;
import vn.edu.ptit.library.search.dto.SearchConfigResponse;

public interface SearchConfigurationService {
    List<SearchConfigResponse> getConfigurations();
    SearchConfigResponse activate(Long id);
}
