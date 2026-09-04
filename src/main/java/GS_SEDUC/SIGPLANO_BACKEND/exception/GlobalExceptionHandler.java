package GS_SEDUC.SIGPLANO_BACKEND.exception;

import GS_SEDUC.SIGPLANO_BACKEND.factory.ResponseFactory;
import GS_SEDUC.SIGPLANO_BACKEND.dto.ApiResponseDTO;
import feign.FeignException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.List;

/**
 * Tratamento centralizado de exceções da aplicação SIGPLANO.
 *
 * <p>Intercepta todas as exceções lançadas por controllers e services,
 * garantindo que o front-end sempre receba um {@link ApiResponseDTO}
 * com HTTP status adequado e mensagens seguras (sem stack trace em produção).</p>
 *
 * <p>Todas as exceções são logadas via SLF4J com classe, método e linha de origem.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // =========================================================================
    // 204 — No Content
    // =========================================================================

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<ApiResponseDTO> handleEmptyResultDataAccessException(EmptyResultDataAccessException ex) {
        logException("Resultado vazio", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.NO_CONTENT);
    }

    // =========================================================================
    // 400 — Bad Request
    // =========================================================================

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponseDTO> handleEntityNotFoundException(EntityNotFoundException ex) {
        logException("Entidade não encontrada", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDTO> handleIllegalArgumentException(IllegalArgumentException ex) {
        logException("Erro de validação", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDTO> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        logException("Tipo de parâmetro inválido", ex);
        return ResponseFactory.erro("Parâmetro inválido: " + ex.getName(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Captura erros de validação do Bean Validation (@Valid).
     * Retorna a lista de mensagens de erro dos campos inválidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.error("Erro de validação de campos: {}", erros);
        return ResponseFactory.erro(String.join("; ", erros), HttpStatus.BAD_REQUEST);
    }

    /**
     * Captura erros de desserialização JSON — por exemplo, quando o frontend envia
     * um campo como objeto mas o backend espera uma String.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDTO> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String mensagem = ex.getMessage();
        String detalhe = "JSON inválido ou formato de campo incorreto.";

        if (mensagem != null && mensagem.contains("Cannot deserialize")) {
            detalhe = "Formato de campo inválido no corpo da requisição. " +
                      "Verifique se todos os campos estão sendo enviados com o tipo correto. " +
                      "Detalhe: " + mensagem.split("\n")[0];
        }

        log.warn("Erro de leitura do corpo JSON: {}", mensagem);
        return ResponseFactory.erro(detalhe, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // 401 — Unauthorized
    // =========================================================================

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponseDTO> handleDisabledException(DisabledException ex) {
        logException("Usuário desativado", ex);
        return ResponseFactory.erro("Usuário desativado. Acesso bloqueado.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponseDTO> handleBadCredentialsException(BadCredentialsException ex) {
        logException("Credenciais inválidas", ex);
        return ResponseFactory.erro("Login ou senha incorretos.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponseDTO> handleSecurityException(SecurityException ex) {
        logException("Violação de segurança", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponseDTO> handleJwtException(JwtException ex) {
        logException("Token JWT inválido", ex);
        return ResponseFactory.erro("Token inválido.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponseDTO> handleExpiredJwtException(ExpiredJwtException ex) {
        logException("Token JWT expirado", ex);
        return ResponseFactory.erro("Sessão expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // 403 — Forbidden
    // =========================================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDTO> handleAccessDeniedException(AccessDeniedException ex) {
        logException("Acesso negado", ex);
        return ResponseFactory.erro("Você não tem permissão para acessar este recurso.", HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 409 — Conflict
    // =========================================================================

    @ExceptionHandler(EntityExistsException.class)
    public ResponseEntity<ApiResponseDTO> handleEntityExistsException(EntityExistsException ex) {
        logException("Entidade duplicada", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseDTO> handleIllegalStateException(IllegalStateException ex) {
        logException("Estado inconsistente", ex);
        return ResponseFactory.erro(ex.getMessage(), HttpStatus.CONFLICT);
    }

    // =========================================================================
    // 503 — Service Unavailable (Integrações externas)
    // =========================================================================

    /**
     * Tratamento para erros na comunicação com serviços externos via Feign (ex: SisErgon).
     * Diferencia erros de autenticação (401/403) de erros genéricos.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponseDTO> handleFeignException(FeignException ex) {
        logException("Erro de comunicação com serviço externo (Feign)", ex);

        int status = ex.status();
        if (status == 401 || status == 403) {
            return ResponseFactory.erro(
                    "Serviço externo recusou o acesso (token pode estar expirado). Tente novamente.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } else if (status == 404) {
            return ResponseFactory.erro("Recurso não encontrado no serviço externo.", HttpStatus.NOT_FOUND);
        }

        return ResponseFactory.erro("Erro ao comunicar com serviço externo. Tente novamente mais tarde.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // =========================================================================
    // 500 — Internal Server Error
    // =========================================================================

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponseDTO> handleIOException(IOException ex) {
        if (isBrokenPipe(ex)) {
            log.warn("Conexão encerrada pelo cliente (broken pipe): {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }

        logException("Erro de IO no servidor", ex);

        if (isDev()) {
            return ResponseFactory.erro("Erro interno: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseFactory.erro("Erro interno de comunicação.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Fallback genérico: captura qualquer exceção não tratada acima.
     * Em DEV expõe a mensagem original; em PROD retorna mensagem genérica.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO> handleGenericException(Exception ex) {
        logException("Erro interno inesperado", ex);

        if (isDev()) {
            return ResponseFactory.erro("Erro interno: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseFactory.erro("Erro interno. Tente novamente mais tarde.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError ex) {
        try {
            log.error("CRÍTICO — OutOfMemoryError detectado!", ex);
        } catch (Throwable ignored) {}

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Erro crítico de memória na aplicação.");
    }

    // =========================================================================
    // Utilitários internos
    // =========================================================================

    /**
     * Log estruturado com classe, método e linha de origem do erro.
     */
    private void logException(String contexto, Exception ex) {
        StackTraceElement[] stack = ex.getStackTrace();
        if (stack.length > 0) {
            StackTraceElement origem = stack[0];
            log.error("{}: {} | Classe: {} | Método: {} | Linha: {}",
                    contexto,
                    ex.getMessage(),
                    origem.getClassName(),
                    origem.getMethodName(),
                    origem.getLineNumber(),
                    ex);
            return;
        }
        log.error("{}: {}", contexto, ex.getMessage(), ex);
    }

    private boolean isBrokenPipe(Throwable ex) {
        return ex.getMessage() != null &&
                (ex.getMessage().contains("Broken pipe") || ex.getMessage().contains("Connection reset by peer"));
    }

    private boolean isDev() {
        return activeProfile.equalsIgnoreCase("dev");
    }
}
