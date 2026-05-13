package com.asg.portediintegration.config;

import com.asg.portediintegration.service.GlobalParameterService;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class OutlookConfig {


    private final GlobalParameterService globalParameterService;

    private static final String TENANT_ID_KEY = "PORT_EDI_OUTLOOK_TENANT_ID";
    private static final String CLIENT_ID_KEY = "PORT_EDI_OUTLOOK_CLIENT_ID";
    private static final String CLIENT_SECRET_KEY = "PORT_EDI_OUTLOOK_CLIENT_SECRET";


    private String getTenantId() {
        return getMandatoryParam(TENANT_ID_KEY);
    }

    private String getClientId() {
        return getMandatoryParam(CLIENT_ID_KEY);
    }

    private String getClientSecret() {
        return getMandatoryParam(CLIENT_SECRET_KEY);
    }

    private String getMandatoryParam(String key) {
        String val = globalParameterService.getValue(key);
        if (StringUtils.isBlank(val)) {
            throw new IllegalStateException("Required global parameter " + key + " is not set");
        }
        return val;
    }

    @Bean
    public TokenCredential tokenCredential() {
        return new ClientSecretCredentialBuilder()
                .tenantId(getTenantId())
                .clientId(getClientId())
                .clientSecret(getClientSecret())
                .build();
    }

    @Bean
    public GraphServiceClient<Request> graphServiceClient(TokenCredential tokenCredential) {
        String scopesStr = globalParameterService.getValue("PORT_EDI_OUTLOOK_SCOPES");
        List<String> scopes;
        if (StringUtils.isBlank(scopesStr)) {
            scopes = List.of("https://graph.microsoft.com/.default");
        } else {
            scopes = Arrays.stream(scopesStr.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
        }
        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(scopes, tokenCredential);
        return GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .buildClient();
    }
}

