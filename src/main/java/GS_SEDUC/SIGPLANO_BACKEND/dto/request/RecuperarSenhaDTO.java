package GS_SEDUC.SIGPLANO_BACKEND.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecuperarSenhaDTO(
    @NotBlank(message = "E-mail é obrigatório.")
    @Email(message = "E-mail inválido.")
    @Size(max = 200, message = "E-mail deve ter no máximo 200 caracteres.")
    String email
) {}
