package GS_SEDUC.SIGPLANO_BACKEND.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrimeiroAcessoDTO(
    @NotBlank(message = "Nova senha é obrigatória.")
    @Size(max = 255, message = "Nova senha deve ter no máximo 255 caracteres.")
    String novaSenha,

    @NotBlank(message = "Confirmação de senha é obrigatória.")
    @Size(max = 255, message = "Confirmação de senha deve ter no máximo 255 caracteres.")
    String confirmacaoSenha
) {}
