package GS_SEDUC.SIGPLANO_BACKEND.iam.service;

import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.SisErgonConsumer;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.SisErgonTokenConfig;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.PessoaSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.VinculoSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.request.SisErgonLoginRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários - SisErgonService")
class SisErgonServiceTest {

    @Mock
    private SisErgonConsumer sisErgonConsumer;

    @Mock
    private SisErgonTokenConfig sisErgonTokenConfig;

    @InjectMocks
    private SisErgonService sisErgonService;

    @Test
    @DisplayName("Deve autenticar com sucesso e definir o token no config")
    void autenticarComSucesso() {
        // Dado
        when(sisErgonTokenConfig.getLogin()).thenReturn("login_mock");
        when(sisErgonTokenConfig.getPassword()).thenReturn("senha_mock");

        LoginResponseDTO responseMock = new LoginResponseDTO("token_123", "Bearer", 1, "API", "api@teste.com", false, 3600L);
        when(sisErgonConsumer.login(any(SisErgonLoginRequestDTO.class))).thenReturn(responseMock);

        // Quando
        sisErgonService.autenticar();

        // Então
        verify(sisErgonTokenConfig, times(1)).setToken("token_123");
    }

    @Test
    @DisplayName("Deve falhar ao autenticar se a API do SisErgon não retornar token")
    void falharAoAutenticar() {
        // Dado
        when(sisErgonTokenConfig.getLogin()).thenReturn("login_mock");
        when(sisErgonTokenConfig.getPassword()).thenReturn("senha_mock");

        when(sisErgonConsumer.login(any(SisErgonLoginRequestDTO.class))).thenReturn(null);

        // Quando / Então
        assertThatThrownBy(() -> sisErgonService.autenticar())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha ao obter token");
    }

    @Test
    @DisplayName("Deve extrair o vínculo mais recente baseado na data de admissão e id")
    void extrairVinculoMaisRecente() {
        // Dado
        VinculoSisErgonDTO vinculo1 = new VinculoSisErgonDTO(10L, 123L, 1, "EXONERADO", "PALMAS", "GESI", "Setor A", "PALMAS", "ESTATUTARIO", "CONTRATADO", "QUADRO", 0, "A", 180, "2020-01-01");
        // Vínculo 2 tem a data mais recente
        VinculoSisErgonDTO vinculo2 = new VinculoSisErgonDTO(20L, 123L, 2, "ATIVO", "PALMAS", "GESI", "Setor B", "PALMAS", "ESTATUTARIO", "CONTRATADO", "QUADRO", 0, "B", 180, "2023-01-01");
        
        PessoaSisErgonDTO pessoa = new PessoaSisErgonDTO(1L, "TESTE", "000", "0", "2000-01-01", "M", "B", "S", "B", "M", List.of(vinculo1, vinculo2));

        // Quando
        Optional<VinculoSisErgonDTO> resultado = sisErgonService.obterVinculoMaisRecente(pessoa);

        // Então
        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(20L); // Puxou o mais recente
        assertThat(resultado.get().situacaoVinculo()).isEqualTo("ATIVO");
    }

    @Test
    @DisplayName("Deve buscar pessoa por CPF repassando a exceção do Feign para o runtime")
    void falhaBuscarPessoa() {
        // Dado
        String cpf = "12345678900";
        when(sisErgonConsumer.findPessoaByCpf(cpf)).thenThrow(new RuntimeException("Connection refused"));

        // Quando / Então
        assertThatThrownBy(() -> sisErgonService.buscarPessoaPorCpf(cpf))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Não foi possível consultar os dados do servidor");
    }
}
