package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponseDTO(
        String token,
        String role,
        Integer codigoRole,
        String nome,
        String email,
        Boolean primeiroAcesso,
        Long tokenExpiraEmMillis
) {}
