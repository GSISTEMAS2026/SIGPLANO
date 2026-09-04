package GS_SEDUC.SIGPLANO_BACKEND.controller.docs;

import GS_SEDUC.SIGPLANO_BACKEND.dto.request.PrimeiroAcessoDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.RecuperarSenhaDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.RedefinirSenhaDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.request.UsuarioLoginRequestDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.response.PrimeiroAcessoResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.dto.ApiResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Documentação Swagger dos endpoints de autenticação e gestão de acesso do SIGPLANO.
 *
 * <p><b>Acesso público:</b> os endpoints {@code /api/auth/login}, {@code /api/auth/logout},
 * {@code /api/auth/recuperar-senha} e {@code /api/auth/redefinir-senha} não exigem autenticação prévia.</p>
 *
 * <p><b>Autenticação necessária:</b> {@code /api/auth/refresh} e {@code /api/auth/primeiro-acesso}
 * exigem, respectivamente, um cookie {@code refreshToken} válido e um token JWT ativo.</p>
 *
 * <p><b>Estratégia de token:</b> o sistema utiliza dois tokens:
 * <ul>
 *   <li><b>Access Token (JWT):</b> curta duração (1h), enviado no header {@code Authorization}.</li>
 *   <li><b>Refresh Token:</b> duração estendida (2h), armazenado em cookie {@code HttpOnly}
 *       e rotacionado a cada uso para mitigar roubo de sessão.</li>
 * </ul>
 */
@Tag(name = "Autenticação", description = "Endpoints de login, logout, refresh, primeiro acesso e recuperação de senha.")
public interface AutenticacaoControllerDocs {

    @Operation(
            summary = "Autenticar usuário (login)",
            description = """
                    Autentica uma conta de setor no sistema e retorna um Access Token JWT,
                    além de configurar um cookie HttpOnly com o Refresh Token.

                    **Identificador de login:** sempre o e-mail institucional da conta de setor
                    (domínio `@seduc.to.gov.br`) — superintendência, planejamento, orçamento ou admin.
                    Não há login por CPF; o CPF só é usado para vincular o responsável por uma conta
                    (ver fluxo de vínculo de responsável, restrito a administradores).

                    **Tokens gerados:**
                    - `accessToken`: JWT de curta duração (1h), usado no header `Authorization: Bearer <token>`.
                    - `refreshToken`: cookie `HttpOnly`, rotacionado automaticamente em cada chamada de refresh (2h).

                    **Observação:** caso seja o primeiro acesso do usuário (`primeiroAcesso = true`),
                    a resposta indicará que é necessário redefinir a senha antes de prosseguir.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login realizado com sucesso. Retorna o token JWT e dados do usuário autenticado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credenciais inválidas — login ou senha incorretos.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno do servidor.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> login(@RequestBody UsuarioLoginRequestDTO dto, HttpServletResponse response);


    @Operation(
            summary = "Renovar Access Token (refresh)",
            description = """
                    Renova o Access Token JWT utilizando o Refresh Token armazenado no cookie `HttpOnly`.

                    **Rotação de Refresh Token:** a cada chamada bem-sucedida, o Refresh Token antigo
                    é invalidado e um novo é emitido via cookie, reduzindo o risco de reutilização indevida.

                    **Pré-requisito:** o cookie `refreshToken` deve estar presente e válido na requisição.
                    Se ausente ou expirado, a resposta será 401.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token renovado com sucesso. Retorna novo Access Token e sua expiração.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Cookie `refreshToken` ausente, inválido ou expirado.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshTokenCookie,
                              HttpServletResponse response);


    @Operation(
            summary = "Encerrar sessão (logout)",
            description = """
                    Encerra a sessão do usuário autenticado de forma segura:

                    **1. Revogação dos Refresh Tokens no banco:**
                    Todos os Refresh Tokens do usuário são excluídos, impedindo renovação de sessão em outros dispositivos.

                    **2. Limpeza do cookie no cliente:**
                    O cookie `refreshToken` é apagado (`maxAge = 0`), removendo-o do navegador.

                    **Recomendação para o frontend:** descarte o Access Token da memória
                    imediatamente após receber a resposta de logout.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sessão encerrada com sucesso. Cookie removido e tokens revogados.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response);


    @Operation(
            summary = "Solicitar recuperação de senha",
            description = """
                    Inicia o fluxo de recuperação de senha enviando um link por e-mail.

                    **Fluxo:**
                    1. O usuário informa o e-mail institucional cadastrado no sistema.
                    2. O sistema gera um token de recuperação com expiração de 15 minutos.
                    3. Um link contendo o token é enviado ao e-mail informado.
                    4. O usuário acessa o link e utiliza o endpoint `/api/auth/redefinir-senha`.

                    **Segurança:** a resposta é sempre 200, mesmo que o e-mail não exista,
                    para evitar enumeração de usuários.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Solicitação processada. Se o e-mail existir, o link de recuperação será enviado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> recuperarSenha(@RequestBody RecuperarSenhaDTO dto);


    @Operation(
            summary = "Redefinir senha via token de recuperação",
            description = """
                    Conclui o fluxo de recuperação de senha utilizando o token recebido por e-mail.

                    **Regras de negócio:**
                    - As senhas informadas devem ser idênticas (`novaSenha` e `confirmacaoSenha`).
                    - O token possui prazo de expiração de 15 minutos.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Senha redefinida com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "As senhas não conferem.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token de recuperação inválido ou expirado.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> redefinirSenha(@RequestBody RedefinirSenhaDTO dto);


    @Operation(
            summary = "Concluir primeiro acesso",
            description = """
                    Permite que o usuário redefina sua senha provisória no primeiro acesso ao sistema.

                    **Quando é acionado:**
                    - Ao realizar login, se `primeiroAcesso = true`, o frontend redireciona para esta tela.

                    **Regras de negócio:**
                    - As senhas informadas devem ser idênticas (`novaSenha` e `confirmacaoSenha`).
                    - A nova senha não pode ser igual à senha provisória definida na criação da conta de setor.
                    - Após a conclusão, a flag `primeiroAcesso` é marcada como `false`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Primeiro acesso concluído com sucesso. Senha atualizada.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PrimeiroAcessoResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "As senhas não conferem ou a nova senha é igual à provisória.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Primeiro acesso já foi concluído anteriormente.",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    ResponseEntity<?> finalizarPrimeiroAcesso(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody PrimeiroAcessoDTO dto);
}
