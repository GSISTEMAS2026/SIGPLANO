package GS_SEDUC.SIGPLANO_BACKEND.service;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.RefreshToken;
import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** Vida do refresh token: 2h após o access token expirar (access = 1h, refresh = 2h extra). */
    private static final long REFRESH_EXPIRATION_TIME_MS = 7_200_000L; // 2h

    private static final SecureRandom RANDOM = new SecureRandom();

    public long getRefreshExpirationTime() {
        return REFRESH_EXPIRATION_TIME_MS;
    }

    /**
     * Gera um novo refresh token opaco, persiste o hash SHA-256 no banco
     * e devolve o valor bruto (que será enviado ao cliente via cookie HttpOnly).
     */
    public String gerarNovoRefreshToken(Usuario usuario) {
        String tokenBruto = gerarTokenAleatorio();

        RefreshToken entidade = new RefreshToken();
        entidade.setTokenHash(sha256(tokenBruto));
        entidade.setUsuario(usuario);
        entidade.setDataExpiracao(LocalDateTime.now().plusNanos(REFRESH_EXPIRATION_TIME_MS * 1_000_000));
        entidade.setRevogado(false);

        refreshTokenRepository.save(entidade);
        log.info("Refresh token gerado para o usuário: {}", usuario.getLogin());

        return tokenBruto;
    }

    /**
     * Valida o refresh token recebido do cookie, retorna o usuário associado
     * e ROTACIONA o token (revoga o antigo, cria um novo).
     * Lança BadCredentialsException se inválido/expirado/revogado.
     */
    public RefreshResultado validarERotacionar(String tokenBruto) {
        if (tokenBruto == null || tokenBruto.isBlank()) {
            log.warn("Tentativa de refresh com token ausente.");
            throw new BadCredentialsException("Refresh token ausente.");
        }

        String hash = sha256(tokenBruto);

        RefreshToken existente = refreshTokenRepository.findByTokenHashAndRevogadoFalse(hash)
                .orElseThrow(() -> {
                    log.warn("Tentativa de refresh com token inválido ou já revogado.");
                    return new BadCredentialsException("Refresh token inválido.");
                });

        if (existente.getDataExpiracao().isBefore(LocalDateTime.now())) {
            existente.setRevogado(true);
            refreshTokenRepository.save(existente);
            log.warn("Refresh token expirado para o usuário ID: {}", existente.getUsuario().getId());
            throw new BadCredentialsException("Refresh token expirado.");
        }

        // Rotação: o token antigo morre, nasce um novo
        existente.setRevogado(true);
        refreshTokenRepository.save(existente);

        String novoTokenBruto = gerarNovoRefreshToken(existente.getUsuario());
        log.info("Refresh token rotacionado com sucesso para o usuário: {}", existente.getUsuario().getLogin());

        return new RefreshResultado(existente.getUsuario(), novoTokenBruto);
    }

    /**
     * Revoga todos os Refresh Tokens ativos de um usuário.
     * Deve ser invocado obrigatoriamente no fluxo de logout.
     */
    @Transactional
    public void revogarTodosDoUsuario(Long usuarioId) {
        refreshTokenRepository.deleteByUsuarioId(usuarioId);
        log.info("Todos os refresh tokens do usuário ID [{}] foram revogados.", usuarioId);
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
        }
    }

    public record RefreshResultado(Usuario usuario, String novoRefreshTokenBruto) {}

    /**
     * Lixeira diária: exclui tokens de atualização expirados e também os já revogados
     * (marcados como tal pela rotação a cada uso ou pelo logout), evitando acúmulo
     * indefinido de registros inúteis na tabela {@code refresh_token}.
     * Roda todos os dias às 3 da manhã.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limpezaTokensInativos() {
        refreshTokenRepository.deleteByDataExpiracaoBefore(LocalDateTime.now());
        refreshTokenRepository.deleteByRevogadoTrue();
        log.info("Limpeza agendada: tokens de refresh expirados e revogados removidos do banco.");
    }
}
