package GS_SEDUC.SIGPLANO_BACKEND.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioLoginRequestDTO(
    @NotBlank(message = "Login é obrigatório.")
    @Size(max = 150, message = "Login deve ter no máximo 150 caracteres.")
    String login,

    @NotBlank(message = "Senha é obrigatória.")
    @Size(max = 255, message = "Senha deve ter no máximo 255 caracteres.")
    String senha
) {}
