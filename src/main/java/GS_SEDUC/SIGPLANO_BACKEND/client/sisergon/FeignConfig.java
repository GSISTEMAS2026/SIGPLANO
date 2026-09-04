package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class FeignConfig {

    private final SisErgonTokenConfig sisErgonTokenConfig;

    public FeignConfig(SisErgonTokenConfig sisErgonTokenConfig) {
        this.sisErgonTokenConfig = sisErgonTokenConfig;
    }

    @Bean
    public RequestInterceptor sisErgonRequestInterceptor() {
        return template -> {
            if (template.url().contains("/auth/login")) {
                return;
            }

            String token = sisErgonTokenConfig.getToken();
            if (token != null && !token.isBlank()) {
                template.header("Authorization", "Bearer " + token);
            }
        };
    }
}
