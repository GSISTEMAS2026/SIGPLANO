package GS_SEDUC.SIGPLANO_BACKEND.dto.response;

public record PrimeiroAcessoResponseDTO(
    boolean sucesso,
    String mensagem,
    String token,
    long expiresIn
) {}
