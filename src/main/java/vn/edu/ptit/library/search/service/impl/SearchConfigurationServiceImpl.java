package vn.edu.ptit.library.search.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.search.dto.SearchConfigResponse;
import vn.edu.ptit.library.search.repository.SearchConfigActivationLock;
import vn.edu.ptit.library.search.repository.SearchVectorConfigRepository;
import vn.edu.ptit.library.search.service.SearchConfigurationService;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchConfigurationServiceImpl implements SearchConfigurationService {
    private final SearchVectorConfigRepository repository;
    private final SearchConfigActivationLock activationLock;

    @Override
    public List<SearchConfigResponse> getConfigurations() {
        return repository.findAll(Sort.by("id")).stream().map(SearchConfigResponse::from).toList();
    }

    @Override
    @Transactional
    public SearchConfigResponse activate(Long id) {
        activationLock.acquire();
        var configs = repository.findAll(Sort.by("id"));
        var selected = configs.stream().filter(config -> config.getId().equals(id)).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SEARCH_CONFIG_NOT_FOUND));
        configs.forEach(config -> config.setIsActive(config.getId().equals(id)));
        repository.saveAllAndFlush(configs);
        return SearchConfigResponse.from(selected);
    }
}
