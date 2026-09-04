package GS_SEDUC.SIGPLANO_BACKEND.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Envelope padrão de resposta da API do SIGPLANO.
 * Todos os endpoints devem retornar respostas encapsuladas por este DTO via {@code ResponseFactory}.
 *
 * @param success   indica se a operação foi bem-sucedida
 * @param status    código HTTP numérico
 * @param message   mensagem descritiva (pode ser nula em respostas de sucesso com data)
 * @param timestamp momento em que a resposta foi gerada
 * @param data      payload de dados (nulo em respostas de erro)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponseDTO(
    boolean success,
    int status,
    String message,
    LocalDateTime timestamp,
    Object data
) {}
