package GS_SEDUC.SIGPLANO_BACKEND.dto.response;

public record LoginResponseDTO(
    String login,
    String role,
    String token,
    long expiresIn,
    boolean primeiroAcesso,
    String nome
) {}
