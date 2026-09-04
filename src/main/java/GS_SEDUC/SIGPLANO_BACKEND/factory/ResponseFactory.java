package GS_SEDUC.SIGPLANO_BACKEND.factory;

import GS_SEDUC.SIGPLANO_BACKEND.dto.ApiResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

/**
 * Fábrica centralizada para criação de respostas padronizadas da API.
 * Garante que toda resposta — de sucesso ou erro — siga o envelope {@link ApiResponseDTO}.
 */
public final class ResponseFactory {

    private ResponseFactory() {}

    /**
     * Resposta de sucesso com dados e mensagem.
     */
    public static <T> ResponseEntity<ApiResponseDTO> sucesso(T data, String message) {
        return ResponseEntity.ok(new ApiResponseDTO(
                true,
                HttpStatus.OK.value(),
                message,
                LocalDateTime.now(),
                data
        ));
    }

    /**
     * Resposta de sucesso com dados (sem mensagem).
     */
    public static <T> ResponseEntity<ApiResponseDTO> sucesso(T data) {
        return ResponseEntity.ok(new ApiResponseDTO(
                true,
                HttpStatus.OK.value(),
                null,
                LocalDateTime.now(),
                data
        ));
    }

    /**
     * Resposta de sucesso apenas com mensagem (sem payload de dados).
     */
    public static ResponseEntity<ApiResponseDTO> sucesso(String message) {
        return ResponseEntity.ok(new ApiResponseDTO(
                true,
                HttpStatus.OK.value(),
                message,
                LocalDateTime.now(),
                null
        ));
    }

    /**
     * Resposta de erro com mensagem e status HTTP customizado.
     */
    public static ResponseEntity<ApiResponseDTO> erro(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(new ApiResponseDTO(
                false,
                status.value(),
                message,
                LocalDateTime.now(),
                null
        ));
    }
}
