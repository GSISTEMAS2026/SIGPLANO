package GS_SEDUC.SIGPLANO_BACKEND.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pessoas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE pessoas SET ativo = false WHERE id=?")
@SQLRestriction("ativo=true")
public class Pessoa extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(length = 200)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy", locale = "pt-BR", timezone = "America/Sao_Paulo")
    private LocalDate dataNascimento;

    @Column(nullable = false)
    private Boolean servidorAtivo = true;

    @Column(length = 100)
    private String setorSigla;

    @Column(length = 100)
    private String regional;

    @Column(length = 200)
    private String setorNome;

    @Column
    private LocalDateTime ultimaSincronizacaoSisErgon;
}
