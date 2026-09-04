package GS_SEDUC.SIGPLANO_BACKEND.model.entity;

import GS_SEDUC.SIGPLANO_BACKEND.model.enums.Role;
import GS_SEDUC.SIGPLANO_BACKEND.model.enums.StatusUsuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE usuarios SET ativo = false WHERE id=?")
@SQLRestriction("ativo=true")
public class Usuario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String login;

    @Column(nullable = false, length = 255)
    private String senha;

    @Column(nullable = false)
    private Boolean primeiroAcesso = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusUsuario status = StatusUsuario.ATIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;
}
