package GS_SEDUC.SIGPLANO_BACKEND.service;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioRepository;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioResponsavelRepository;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.PrimeiroAcessoDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.RedefinirSenhaDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.UsuarioLoginRequestDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.response.PrimeiroAcessoResponseDTO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioResponsavelRepository usuarioResponsavelRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.sigplano.url}")
    private String sigplanoUrl;

    @Value("${app.sigplano.endpoint}")
    private String recuperarSenhaEndpoint;

    public LoginResponseDTO login(UsuarioLoginRequestDTO dto) {
        log.info("Tentativa de login para o usuário: {}", dto.login());
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.login(), dto.senha())
        );

        Usuario usuario = usuarioRepository.findByLogin(dto.login())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!usuario.isAtivo()) {
            throw new IllegalArgumentException("Usuário inativo. Acesso bloqueado.");
        }

        String token = jwtService.gerarAccessToken(usuario);

        String nomeResponsavel = usuarioResponsavelRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .map(vinculo -> vinculo.getPessoa().getNome())
                .orElse(null);

        return new LoginResponseDTO(
                usuario.getLogin(),
                usuario.getRole().name(),
                token,
                jwtService.getAccessExpirationTime(),
                usuario.getPrimeiroAcesso(),
                nomeResponsavel
        );
    }

    public PrimeiroAcessoResponseDTO finalizarPrimeiroAcesso(String login, PrimeiroAcessoDTO dto) {
        log.info("Processando primeiro acesso para o usuário: {}", login);
        
        Usuario usuario = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!usuario.getPrimeiroAcesso()) {
            throw new IllegalArgumentException("Primeiro acesso já foi realizado.");
        }
        
        if (!dto.novaSenha().equals(dto.confirmacaoSenha())) {
            throw new IllegalArgumentException("As senhas não conferem.");
        }

        if (passwordEncoder.matches(dto.novaSenha(), usuario.getSenha())) {
            throw new IllegalArgumentException("A nova senha não pode ser igual à senha provisória.");
        }

        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.setPrimeiroAcesso(false);
        usuarioRepository.save(usuario);

        String novoToken = jwtService.gerarAccessToken(usuario);

        return new PrimeiroAcessoResponseDTO(
                true,
                "Primeiro acesso finalizado. Senha redefinida com sucesso.",
                novoToken,
                jwtService.getAccessExpirationTime()
        );
    }

    public void enviarEmailRecuperacaoSenha(String email) {
        Optional<Usuario> optionalUsuario = usuarioRepository.findByLogin(email);
        
        if (optionalUsuario.isEmpty() || !optionalUsuario.get().isAtivo()) {
            // Em produção não devemos indicar se o e-mail existe, retornamos normal
            log.info("Solicitação de recuperação para e-mail inexistente ou inativo: {}", email);
            return;
        }

        Usuario usuario = optionalUsuario.get();
        String token = jwtService.generatePasswordRecoveryToken(usuario.getLogin());
        String link = sigplanoUrl + recuperarSenhaEndpoint + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("SIGPLANO - Recuperação de Senha");
        message.setText("Olá, \n\nVocê solicitou a recuperação de senha do SIGPLANO.\n" +
                "Clique no link abaixo para redefinir sua senha (expira em 15 minutos):\n\n" + link +
                "\n\nSe você não solicitou, ignore este e-mail.");

        mailSender.send(message);
        log.info("E-mail de recuperação de senha enviado para: {}", email);
    }

    public void redefinirSenha(RedefinirSenhaDTO dto) {
        if (!jwtService.tokenValido(dto.token())) {
            throw new IllegalArgumentException("Token inválido ou expirado.");
        }

        Claims claims = jwtService.extrairClaims(dto.token());
        if (!"PASSWORD_RECOVERY".equals(claims.get("type"))) {
            throw new IllegalArgumentException("Token inválido para esta operação.");
        }

        String login = claims.getSubject();
        Usuario usuario = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!dto.novaSenha().equals(dto.confirmacaoSenha())) {
            throw new IllegalArgumentException("As senhas não conferem.");
        }

        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuario);
        log.info("Senha redefinida com sucesso para: {}", login);
    }
}
