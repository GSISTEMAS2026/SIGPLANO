package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class SisErgonTokenConfig {

    @Value("${app.sisergon.login}")
    private String login;

    @Value("${app.sisergon.password}")
    private String password;

    private volatile String token;
}
