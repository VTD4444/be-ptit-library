package vn.edu.ptit.library.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.microsoft")
public class MicrosoftAuthProperties {

    private boolean enabled = true;

    private String clientId = "";

    private String jwkSetUri = "https://login.microsoftonline.com/common/discovery/v2.0/keys";

    private String allowedTenantIds = "";

    private String allowedDomains = "stu.ptit.edu.vn,ptit.edu.vn";

    private boolean autoProvision = true;

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(clientId);
    }

    public List<String> getAllowedTenantIdList() {
        return splitCsvLowercase(allowedTenantIds);
    }

    public List<String> getAllowedDomainList() {
        return splitCsvLowercase(allowedDomains);
    }

    private static List<String> splitCsvLowercase(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
