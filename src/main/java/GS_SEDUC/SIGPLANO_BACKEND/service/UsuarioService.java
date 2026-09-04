package GS_SEDUC.SIGPLANO_BACKEND.service;

import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.PessoaSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.VinculoSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.CriarContaSetorDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.VincularResponsavelDTO;
import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Pessoa;
import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.model.entity.UsuarioResponsavel;
import GS_SEDUC.SIGPLANO_BACKEND.model.enums.StatusUsuario;
import GS_SEDUC.SIGPLANO_BACKEND.repository.PessoaRepository;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioRepository;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioResponsavelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioResponsavelRepository usuarioResponsavelRepository;
    private final SisErgonService sisErgonService;
    private final PasswordEncoder passwordEncoder;

    private static final String DOMINIO_INSTITUCIONAL = "@seduc.to.gov.br";

    /**
     * Fluxo A: cria uma conta de setor (SUPERINTENDENCIA, PLANEJAMENTO, ORCAMENTO ou ADMIN).
     * Login é sempre um e-mail institucional; a conta nasce sem responsável vinculado
     * (vínculo é opcional e feito separadamente via {@link #vincularResponsavel}).
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public Usuario cadastrarContaSetor(CriarContaSetorDTO dto) {
        log.info("Iniciando cadastro de conta de setor: {}", dto.login());

        if (usuarioRepository.findByLogin(dto.login()).isPresent()) {
            log.warn("Falha no cadastro: o login [{}] já está cadastrado no sistema.", dto.login());
            throw new IllegalArgumentException("O login informado já está cadastrado.");
        }

        if (!dto.login().endsWith(DOMINIO_INSTITUCIONAL)) {
            log.warn("Falha no cadastro da conta [{}]: domínio inválido, esperado {}.", dto.login(), DOMINIO_INSTITUCIONAL);
            throw new IllegalArgumentException("Contas de setor devem obrigatoriamente utilizar o domínio " + DOMINIO_INSTITUCIONAL);
        }

        Usuario usuario = Usuario.builder()
                .login(dto.login())
                .senha(passwordEncoder.encode(dto.senha()))
                .primeiroAcesso(true)
                .status(StatusUsuario.ATIVO)
                .role(dto.role())
                .build();

        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        log.info("Conta de setor [{}] cadastrada com sucesso (ID: {}, Role: {}).",
                usuarioSalvo.getLogin(), usuarioSalvo.getId(), usuarioSalvo.getRole());
        return usuarioSalvo;
    }

    /**
     * Fluxo B: vincula (ou troca) o responsável pelo acesso de uma conta de setor.
     * Valida a pessoa contra o SisErgon (CPF, data de nascimento e vínculo ativo — mesma
     * regra usada para servidores) e nunca sobrescreve: se já havia um responsável ativo,
     * ele é desativado e um novo registro é inserido, preservando o histórico.
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public UsuarioResponsavel vincularResponsavel(VincularResponsavelDTO dto) {
        log.info("Vinculando responsável (CPF [{}]) à conta de setor ID [{}]...", dto.cpf(), dto.usuarioId());

        Usuario usuario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new IllegalArgumentException("Conta de setor não encontrada."));

        Pessoa pessoa = validarESincronizarPessoaComErgon(dto.cpf(), dto.dataNascimento());

        usuarioResponsavelRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .ifPresent(vinculoAtual -> {
                    vinculoAtual.setAtivo(false);
                    usuarioResponsavelRepository.save(vinculoAtual);
                    log.info("Vínculo de responsável anterior (Pessoa ID [{}]) desativado para a conta [{}].",
                            vinculoAtual.getPessoa().getId(), usuario.getLogin());
                });

        UsuarioResponsavel novoVinculo = UsuarioResponsavel.builder()
                .usuario(usuario)
                .pessoa(pessoa)
                .build();

        UsuarioResponsavel vinculoSalvo = usuarioResponsavelRepository.save(novoVinculo);
        log.info("Responsável vinculado com sucesso: Pessoa [{}] agora responde pela conta [{}].",
                pessoa.getNome(), usuario.getLogin());
        return vinculoSalvo;
    }

    private Pessoa validarESincronizarPessoaComErgon(String cpf, LocalDate dataNascimentoInformada) {
        log.info("Consultando CPF [{}] na base do SisErgon...", cpf);
        PessoaSisErgonDTO pessoaSisErgon = sisErgonService.buscarPessoaPorCpf(cpf);
        if (pessoaSisErgon == null) {
            log.warn("Falha ao vincular responsável: CPF [{}] não encontrado no SisErgon.", cpf);
            throw new IllegalArgumentException("Servidor não encontrado na base do SisErgon.");
        }

        LocalDate dataNascimentoErgon = LocalDate.parse(pessoaSisErgon.dataNascimento(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        if (!dataNascimentoErgon.equals(dataNascimentoInformada)) {
            log.warn("Falha ao vincular responsável: data de nascimento informada diverge da base estadual para o CPF [{}].", cpf);
            throw new IllegalArgumentException("Data de nascimento não confere com os dados da base estadual.");
        }

        VinculoSisErgonDTO vinculoAtivo = sisErgonService.obterVinculoMaisRecente(pessoaSisErgon)
                .orElseThrow(() -> {
                    log.warn("Falha ao vincular responsável: CPF [{}] sem vínculos registrados.", cpf);
                    return new IllegalArgumentException("Servidor não possui vínculos registrados.");
                });

        if (vinculoAtivo.situacaoVinculo() != null &&
                (vinculoAtivo.situacaoVinculo().contains("DESATIVADO") || vinculoAtivo.situacaoVinculo().contains("EXONERADO"))) {
            log.warn("Falha ao vincular responsável: vínculo do CPF [{}] consta inativo/exonerado (Situação: {}).",
                    cpf, vinculoAtivo.situacaoVinculo());
            throw new IllegalArgumentException("Não foi possível vincular o responsável. Vínculo inativo ou exonerado. Contate a administração do sistema.");
        }

        Pessoa pessoa = pessoaRepository.findByCpf(cpf).orElse(new Pessoa());
        pessoa.setNome(pessoaSisErgon.nome());
        pessoa.setCpf(pessoaSisErgon.cpf());
        pessoa.setDataNascimento(dataNascimentoErgon);
        pessoa.setServidorAtivo(true);
        pessoa.setSetorSigla(vinculoAtivo.setorSigla());
        pessoa.setRegional(vinculoAtivo.regional());
        pessoa.setSetorNome(vinculoAtivo.setorNome());
        pessoa.setUltimaSincronizacaoSisErgon(LocalDateTime.now());

        Pessoa pessoaSalva = pessoaRepository.save(pessoa);
        log.info("Dados funcionais sincronizados com o Ergon para o CPF [{}]. Lotação identificada: {}",
                cpf, pessoaSalva.getSetorSigla());
        return pessoaSalva;
    }
}
