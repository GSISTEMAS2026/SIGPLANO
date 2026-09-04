package GS_SEDUC.SIGPLANO_BACKEND.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Histórico de quem é/foi o responsável por operar uma conta de setor ({@link Usuario}).
 *
 * <p>Não usa {@code @SQLDelete}/{@code @SQLRestriction} como as demais entidades do módulo IAM:
 * o objetivo aqui é justamente preservar e permitir consultar o histórico completo de
 * responsáveis. O campo {@code ativo} (herdado de {@link BaseEntity}) é tratado como uma
 * coluna de negócio comum — {@code true} identifica o vínculo atual; ao trocar de responsável,
 * o vínculo antigo é marcado como {@code false} e um novo registro é inserido, nunca sobrescrito.</p>
 */
@Entity
@Table(name = "usuario_responsavel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioResponsavel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;
}
