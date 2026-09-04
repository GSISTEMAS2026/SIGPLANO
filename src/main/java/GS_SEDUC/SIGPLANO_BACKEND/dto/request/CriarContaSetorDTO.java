package GS_SEDUC.SIGPLANO_BACKEND.dto.request;

import GS_SEDUC.SIGPLANO_BACKEND.model.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarContaSetorDTO(
    @NotBlank(message = "Login é obrigatório.")
    @Size(max = 150, message = "Login deve ter no máximo 150 caracteres.")
    String login, // e-mail institucional, domínio @seduc.to.gov.br

    @NotBlank(message = "Senha é obrigatória.")
    @Size(max = 255, message = "Senha deve ter no máximo 255 caracteres.")
    String senha,

    @NotNull(message = "Perfil (Role) é obrigatório.")
    Role role
) {}
