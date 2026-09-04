package GS_SEDUC.SIGPLANO_BACKEND.config;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.model.enums.Role;
import GS_SEDUC.SIGPLANO_BACKEND.model.enums.StatusUsuario;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Carga inicial de dados exclusiva do ambiente de desenvolvimento (perfil {@code dev}).
 * Cria a conta de setor ADMIN com senha provisória fixa — nunca deve rodar fora de dev,
 * por isso é restrita via {@link Profile}. O responsável por essa conta pode ser
 * vinculado depois, pelo próprio fluxo de vínculo (não é obrigatório no seed).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataLoaderRunner implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Executando carga inicial (DataLoaderRunner)...");

        String adminEmail = "gsistemas@seduc.to.gov.br";
        Optional<Usuario> adminExistente = usuarioRepository.findByLogin(adminEmail);

        if (adminExistente.isEmpty()) {
            log.info("Criando usuário master de sistema ({})...", adminEmail);
            Usuario adminMaster = Usuario.builder()
                    .login(adminEmail)
                    .senha(passwordEncoder.encode("123456"))
                    .primeiroAcesso(false) // Já nasce com acesso liberado sem forçar troca se não quiser
                    .status(StatusUsuario.ATIVO)
                    .role(Role.ADMIN)
                    .build();

            usuarioRepository.save(adminMaster);
            log.info("Usuário master criado com sucesso.");
        } else {
            log.info("Usuário master já existe.");
        }

        log.info("Carga inicial concluída.");
    }
}
