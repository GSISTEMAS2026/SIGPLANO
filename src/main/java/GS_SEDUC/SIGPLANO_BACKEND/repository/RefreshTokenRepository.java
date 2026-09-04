package GS_SEDUC.SIGPLANO_BACKEND.repository;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevogadoFalse(String tokenHash);

    void deleteByUsuarioId(Long usuarioId);

    void deleteByDataExpiracaoBefore(LocalDateTime dataExpiracao);

    void deleteByRevogadoTrue();
}
