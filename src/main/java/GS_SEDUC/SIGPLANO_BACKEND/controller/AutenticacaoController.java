package GS_SEDUC.SIGPLANO_BACKEND.controller;

import GS_SEDUC.SIGPLANO_BACKEND.controller.docs.AutenticacaoControllerDocs;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.PrimeiroAcessoDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.RecuperarSenhaDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.RedefinirSenhaDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.UsuarioLoginRequestDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.repository.UsuarioRepository;
import GS_SEDUC.SIGPLANO_BACKEND.service.AutenticacaoService;
import GS_SEDUC.SIGPLANO_BACKEND.service.JwtService;
import GS_SEDUC.SIGPLANO_BACKEND.service.RefreshTokenService;
import GS_SEDUC.SIGPLANO_BACKEND.factory.ResponseFactory;
import GS_SEDUC.SIGPLANO_BACKEND.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AutenticacaoController implements AutenticacaoControllerDocs {

    private final AutenticacaoService autenticacaoService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.security.cookie-secure}")
    private boolean cookieSecure;

    @Override
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid UsuarioLoginRequestDTO dto, HttpServletResponse response) {
        LoginResponseDTO loginResponse = autenticacaoService.login(dto);

        Usuario usuario = usuarioRepository.findByLogin(dto.login()).orElseThrow();
        String refreshToken = refreshTokenService.gerarNovoRefreshToken(usuario);
        adicionarCookieRefresh(response, refreshToken);

        return ResponseFactory.sucesso(loginResponse, "Login realizado com sucesso.");
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshTokenCookie,
                                     HttpServletResponse response) {
        RefreshTokenService.RefreshResultado resultado = refreshTokenService.validarERotacionar(refreshTokenCookie);

        String novoAccessToken = jwtService.gerarAccessToken(resultado.usuario());
        adicionarCookieRefresh(response, resultado.novoRefreshTokenBruto());

        Map<String, Object> dados = Map.of(
                "token", novoAccessToken,
                "expiresIn", jwtService.getAccessExpirationTime()
        );

        return ResponseFactory.sucesso(dados, "Token renovado com sucesso.");
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UsuarioAutenticado usuarioAutenticado) {
            refreshTokenService.revogarTodosDoUsuario(usuarioAutenticado.getId());
        }

        limparCookieRefresh(response);
        return ResponseFactory.sucesso("Logout realizado com sucesso.");
    }

    @Override
    @PostMapping("/recuperar-senha")
    public ResponseEntity<?> recuperarSenha(@RequestBody @Valid RecuperarSenhaDTO dto) {
        autenticacaoService.enviarEmailRecuperacaoSenha(dto.email());
        return ResponseFactory.sucesso("Link de recuperação enviado com sucesso se o e-mail existir na base.");
    }

    @Override
    @PostMapping("/redefinir-senha")
    public ResponseEntity<?> redefinirSenha(@RequestBody @Valid RedefinirSenhaDTO dto) {
        autenticacaoService.redefinirSenha(dto);
        return ResponseFactory.sucesso("Senha redefinida com sucesso.");
    }

    @Override
    @PutMapping("/primeiro-acesso")
    public ResponseEntity<?> finalizarPrimeiroAcesso(@AuthenticationPrincipal UserDetails userDetails,
                                                     @RequestBody @Valid PrimeiroAcessoDTO dto) {
        return ResponseFactory.sucesso(autenticacaoService.finalizarPrimeiroAcesso(userDetails.getUsername(), dto), "Primeiro acesso finalizado.");
    }

    private void adicionarCookieRefresh(HttpServletResponse response, String tokenBruto) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenBruto)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshTokenService.getRefreshExpirationTime()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void limparCookieRefresh(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
