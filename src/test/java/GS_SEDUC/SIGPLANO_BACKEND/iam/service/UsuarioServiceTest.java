package GS_SEDUC.SIGPLANO_BACKEND.iam.service;

import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.PessoaSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.client.sisergon.dto.VinculoSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.dto.request.CriarContaSetorDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.dto.request.VincularResponsavelDTO;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.entity.Pessoa;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.entity.UsuarioResponsavel;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.enums.Role;
import GS_SEDUC.SIGPLANO_BACKEND.iam.models.enums.StatusUsuario;
import GS_SEDUC.SIGPLANO_BACKEND.iam.repository.PessoaRepository;
import GS_SEDUC.SIGPLANO_BACKEND.iam.repository.UsuarioRepository;
import GS_SEDUC.SIGPLANO_BACKEND.iam.repository.UsuarioResponsavelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários - UsuarioService")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private UsuarioResponsavelRepository usuarioResponsavelRepository;

    @Mock
    private SisErgonService sisErgonService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private final String CPF_VALIDO = "05606928116";
    private final LocalDate DATA_NASCIMENTO = LocalDate.of(2003, 12, 22);

    // ---------------------------------------------------------------
    // Fluxo A: cadastrarContaSetor
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve cadastrar conta de setor com e-mail @seduc.to.gov.br")
    void cadastrarContaSetorComSucesso() {
        // Dado
        String email = "planejamento@seduc.to.gov.br";
        CriarContaSetorDTO dto = new CriarContaSetorDTO(email, "senhaForte123", Role.PLANEJAMENTO);

        when(usuarioRepository.findByLogin(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senhaForte123")).thenReturn("senha_criptografada");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando
        Usuario usuario = usuarioService.cadastrarContaSetor(dto);

        // Então
        assertThat(usuario).isNotNull();
        assertThat(usuario.getLogin()).isEqualTo(email);
        assertThat(usuario.getSenha()).isEqualTo("senha_criptografada");
        assertThat(usuario.getPrimeiroAcesso()).isTrue();
        assertThat(usuario.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(usuario.getRole()).isEqualTo(Role.PLANEJAMENTO);

        verify(sisErgonService, never()).buscarPessoaPorCpf(anyString());
    }

    @Test
    @DisplayName("Deve rejeitar conta de setor se e-mail não for do domínio @seduc.to.gov.br")
    void rejeitarContaSetorSemDominioSeduc() {
        // Dado
        String email = "planejamento@gmail.com";
        CriarContaSetorDTO dto = new CriarContaSetorDTO(email, "senha123", Role.PLANEJAMENTO);

        when(usuarioRepository.findByLogin(email)).thenReturn(Optional.empty());

        // Quando / Então
        assertThatThrownBy(() -> usuarioService.cadastrarContaSetor(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@seduc.to.gov.br");
    }

    @Test
    @DisplayName("Deve rejeitar conta de setor se o login já estiver cadastrado")
    void rejeitarContaSetorComLoginDuplicado() {
        // Dado
        String email = "orcamento@seduc.to.gov.br";
        CriarContaSetorDTO dto = new CriarContaSetorDTO(email, "senha123", Role.ORCAMENTO);

        when(usuarioRepository.findByLogin(email)).thenReturn(Optional.of(new Usuario()));

        // Quando / Então
        assertThatThrownBy(() -> usuarioService.cadastrarContaSetor(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está cadastrado");
    }

    // ---------------------------------------------------------------
    // Fluxo B: vincularResponsavel
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve vincular responsável verificando base SisErgon e vínculo mais recente")
    void vincularResponsavelComSucesso() {
        // Dado
        Usuario contaSetor = Usuario.builder().id(10L).login("planejamento@seduc.to.gov.br").role(Role.PLANEJAMENTO).build();
        VincularResponsavelDTO dto = new VincularResponsavelDTO(10L, CPF_VALIDO, DATA_NASCIMENTO);

        VinculoSisErgonDTO vinculo = new VinculoSisErgonDTO(1L, 11811501L, 5, "ATIVO", "PALMAS", "GESI", "Gerência de Sistemas", "PALMAS", "ESTATUTARIO", "CONTRATADO", "QUADRO", 0, "009-1-A", 180, "2025-04-01");
        PessoaSisErgonDTO pessoaSisErgon = new PessoaSisErgonDTO(1L, "RAFAEL", CPF_VALIDO, "123", "2003-12-22", "M", "PARDO", "SOLTEIRO", "BRASILEIRA", "MEDIO", List.of(vinculo));

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(contaSetor));
        when(sisErgonService.buscarPessoaPorCpf(CPF_VALIDO)).thenReturn(pessoaSisErgon);
        when(sisErgonService.obterVinculoMaisRecente(pessoaSisErgon)).thenReturn(Optional.of(vinculo));
        when(pessoaRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());
        when(pessoaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioResponsavelRepository.findByUsuarioIdAndAtivoTrue(10L)).thenReturn(Optional.empty());
        when(usuarioResponsavelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando
        UsuarioResponsavel vinculoCriado = usuarioService.vincularResponsavel(dto);

        // Então
        assertThat(vinculoCriado).isNotNull();
        assertThat(vinculoCriado.getUsuario()).isEqualTo(contaSetor);
        assertThat(vinculoCriado.getPessoa().getNome()).isEqualTo("RAFAEL");
        assertThat(vinculoCriado.getPessoa().getSetorSigla()).isEqualTo("GESI");
    }

    @Test
    @DisplayName("Deve desativar o vínculo anterior (sem sobrescrever) ao trocar de responsável")
    void trocarResponsavelDesativaVinculoAnterior() {
        // Dado
        Usuario contaSetor = Usuario.builder().id(10L).login("planejamento@seduc.to.gov.br").role(Role.PLANEJAMENTO).build();
        VincularResponsavelDTO dto = new VincularResponsavelDTO(10L, CPF_VALIDO, DATA_NASCIMENTO);

        Pessoa pessoaAntiga = new Pessoa();
        pessoaAntiga.setId(99L);
        UsuarioResponsavel vinculoAntigo = UsuarioResponsavel.builder().id(1L).usuario(contaSetor).pessoa(pessoaAntiga).build();

        VinculoSisErgonDTO vinculo = new VinculoSisErgonDTO(1L, 11811501L, 5, "ATIVO", "PALMAS", "GESI", "Gerência de Sistemas", "PALMAS", "ESTATUTARIO", "CONTRATADO", "QUADRO", 0, "009-1-A", 180, "2025-04-01");
        PessoaSisErgonDTO pessoaSisErgon = new PessoaSisErgonDTO(1L, "RAFAEL", CPF_VALIDO, "123", "2003-12-22", "M", "PARDO", "SOLTEIRO", "BRASILEIRA", "MEDIO", List.of(vinculo));

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(contaSetor));
        when(sisErgonService.buscarPessoaPorCpf(CPF_VALIDO)).thenReturn(pessoaSisErgon);
        when(sisErgonService.obterVinculoMaisRecente(pessoaSisErgon)).thenReturn(Optional.of(vinculo));
        when(pessoaRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());
        when(pessoaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioResponsavelRepository.findByUsuarioIdAndAtivoTrue(10L)).thenReturn(Optional.of(vinculoAntigo));
        when(usuarioResponsavelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando
        usuarioService.vincularResponsavel(dto);

        // Então
        assertThat(vinculoAntigo.isAtivo()).isFalse();
        verify(usuarioResponsavelRepository, times(2)).save(any(UsuarioResponsavel.class));
    }

    @Test
    @DisplayName("Deve rejeitar vínculo se a conta de setor não existir")
    void rejeitarVinculoComContaSetorInexistente() {
        // Dado
        VincularResponsavelDTO dto = new VincularResponsavelDTO(999L, CPF_VALIDO, DATA_NASCIMENTO);
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        // Quando / Então
        assertThatThrownBy(() -> usuarioService.vincularResponsavel(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conta de setor não encontrada");
    }

    @Test
    @DisplayName("Deve rejeitar vínculo se o vínculo atual constar como DESATIVADO no Ergon")
    void rejeitarVinculoComSituacaoDesativada() {
        // Dado
        Usuario contaSetor = Usuario.builder().id(10L).login("planejamento@seduc.to.gov.br").role(Role.PLANEJAMENTO).build();
        VincularResponsavelDTO dto = new VincularResponsavelDTO(10L, CPF_VALIDO, DATA_NASCIMENTO);

        VinculoSisErgonDTO vinculo = new VinculoSisErgonDTO(1L, 11811501L, 5, "DESATIVADO", "PALMAS", "GESI", "Gerência de Sistemas", "PALMAS", "ESTATUTARIO", "CONTRATADO", "QUADRO", 0, "009-1-A", 180, "2025-04-01");
        PessoaSisErgonDTO pessoaSisErgon = new PessoaSisErgonDTO(1L, "RAFAEL", CPF_VALIDO, "123", "2003-12-22", "M", "PARDO", "SOLTEIRO", "BRASILEIRA", "MEDIO", List.of(vinculo));

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(contaSetor));
        when(sisErgonService.buscarPessoaPorCpf(CPF_VALIDO)).thenReturn(pessoaSisErgon);
        when(sisErgonService.obterVinculoMaisRecente(pessoaSisErgon)).thenReturn(Optional.of(vinculo));

        // Quando / Então
        assertThatThrownBy(() -> usuarioService.vincularResponsavel(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vínculo inativo ou exonerado");
    }

    @Test
    @DisplayName("Deve rejeitar vínculo se a data de nascimento não bater com a base estadual")
    void rejeitarVinculoComDataNascimentoDivergente() {
        // Dado
        Usuario contaSetor = Usuario.builder().id(10L).login("planejamento@seduc.to.gov.br").role(Role.PLANEJAMENTO).build();
        LocalDate dataDivergente = LocalDate.of(1990, 1, 1);
        VincularResponsavelDTO dto = new VincularResponsavelDTO(10L, CPF_VALIDO, dataDivergente);

        PessoaSisErgonDTO pessoaSisErgon = new PessoaSisErgonDTO(1L, "RAFAEL", CPF_VALIDO, "123", "2003-12-22", "M", "PARDO", "SOLTEIRO", "BRASILEIRA", "MEDIO", List.of());

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(contaSetor));
        when(sisErgonService.buscarPessoaPorCpf(CPF_VALIDO)).thenReturn(pessoaSisErgon);

        // Quando / Então
        assertThatThrownBy(() -> usuarioService.vincularResponsavel(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data de nascimento não confere");
    }
}
