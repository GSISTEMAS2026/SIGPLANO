package GS_SEDUC.SIGPLANO_BACKEND.service;

import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.SisErgonConsumer;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.SisErgonTokenConfig;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.PessoaSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.request.SisErgonLoginRequestDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.VinculoSisErgonDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SisErgonService {

    private final SisErgonConsumer sisErgonConsumer;
    private final SisErgonTokenConfig sisErgonTokenConfig;

    @PostConstruct
    public void init() {
        try {
            autenticar();
        } catch (Exception e) {
            log.warn("Falha ao autenticar no SisErgon na inicialização: {}", e.getMessage());
        }
    }

    public synchronized void autenticar() {
        log.info("Iniciando autenticação no SisErgon...");
        SisErgonLoginRequestDTO request = new SisErgonLoginRequestDTO(
            sisErgonTokenConfig.getLogin(),
            sisErgonTokenConfig.getPassword()
        );
        LoginResponseDTO response = sisErgonConsumer.login(request);
        if (response != null && response.token() != null) {
            sisErgonTokenConfig.setToken(response.token());
            log.info("Autenticação no SisErgon realizada com sucesso.");
        } else {
            log.error("A API do SisErgon não retornou um token válido para as credenciais configuradas.");
            throw new RuntimeException("Falha ao obter token do SisErgon.");
        }
    }

    @Scheduled(fixedDelay = 50 * 60 * 1000) // Renova a cada 50min
    public void renovarToken() {
        try {
            autenticar();
        } catch (Exception e) {
            log.error("Falha ao renovar token do SisErgon: {}", e.getMessage());
        }
    }

    public PessoaSisErgonDTO buscarPessoaPorCpf(String cpf) {
        try {
            return sisErgonConsumer.findPessoaByCpf(cpf).getBody();
        } catch (Exception e) {
            log.error("Erro ao buscar pessoa por CPF no SisErgon: {}", e.getMessage());
            throw new RuntimeException("Não foi possível consultar os dados do servidor no momento.");
        }
    }

    /**
     * Retorna o vínculo mais recente da pessoa baseando-se na data de admissão e/ou id.
     */
    public Optional<VinculoSisErgonDTO> obterVinculoMaisRecente(PessoaSisErgonDTO pessoaDTO) {
        if (pessoaDTO == null || pessoaDTO.vinculos() == null || pessoaDTO.vinculos().isEmpty()) {
            return Optional.empty();
        }
        
        return pessoaDTO.vinculos().stream()
            .max(Comparator.comparing(VinculoSisErgonDTO::dataAdmissao)
                           .thenComparing(VinculoSisErgonDTO::id));
    }
}
