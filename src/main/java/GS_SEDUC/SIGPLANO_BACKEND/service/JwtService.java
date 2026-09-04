package GS_SEDUC.SIGPLANO_BACKEND.service;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;

    private static final long ACCESS_EXPIRATION_TIME = 3600000L; // 1h
    private static final long PASSWORD_RECOVER_EXPIRATION_TIME = 900000L; // 15min

    public JwtService(@Value("${security.jwt.secret-key}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String gerarAccessToken(Usuario usuario) {
        return Jwts.builder()
                .setSubject(usuario.getLogin())
                .setId(UUID.randomUUID().toString())
                .claim("role", usuario.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION_TIME))
                .signWith(secretKey)
                .compact();
    }

    public long getAccessExpirationTime() {
        return ACCESS_EXPIRATION_TIME;
    }

    public String extrairLogin(String token) {
        return extrairClaims(token).getSubject();
    }

    public boolean tokenValido(String token) {
        try {
            extrairLogin(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims extrairClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generatePasswordRecoveryToken(String login) {
        return Jwts.builder()
                .setSubject(login)
                .claim("type", "PASSWORD_RECOVERY")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + PASSWORD_RECOVER_EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
