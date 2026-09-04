package GS_SEDUC.SIGPLANO_BACKEND.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VincularResponsavelDTO(
    @NotNull(message = "Conta de setor é obrigatória.")
    Long usuarioId,

    @NotBlank(message = "CPF é obrigatório.")
    @Size(min = 11, max = 11, message = "CPF deve conter 11 dígitos.")
    String cpf,

    @NotNull(message = "Data de nascimento é obrigatória.")
    LocalDate dataNascimento
) {}
