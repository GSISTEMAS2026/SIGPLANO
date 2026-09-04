package GS_SEDUC.SIGPLANO_BACKEND.security;

import GS_SEDUC.SIGPLANO_BACKEND.model.entity.Usuario;
import GS_SEDUC.SIGPLANO_BACKEND.model.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * {@link UserDetails} da conta de setor autenticada. Carrega o {@code id} e a {@link Role}
 * diretamente no principal do {@code SecurityContext}, para que qualquer Service consiga
 * saber "quem está logado" (ex.: para aplicar escopo de dados por Specification, no futuro
 * módulo de planejamento/orçamento) sem precisar buscar o {@link Usuario} de novo no banco.
 */
public class UsuarioAutenticado implements UserDetails {

    private final Long id;
    private final String login;
    private final String senha;
    private final Role role;
    private final boolean ativo;

    public UsuarioAutenticado(Long id, String login, String senha, Role role, boolean ativo) {
        this.id = id;
        this.login = login;
        this.senha = senha;
        this.role = role;
        this.ativo = ativo;
    }

    public static UsuarioAutenticado from(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(),
                usuario.getLogin(),
                usuario.getSenha(),
                usuario.getRole(),
                usuario.isAtivo()
        );
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
