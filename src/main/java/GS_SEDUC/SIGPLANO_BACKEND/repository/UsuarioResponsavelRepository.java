package GS_SEDUC.SIGPLANO_BACKEND.repository;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.UsuarioResponsavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioResponsavelRepository extends JpaRepository<UsuarioResponsavel, Long> {

    Optional<UsuarioResponsavel> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    List<UsuarioResponsavel> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);
}
