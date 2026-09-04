package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PessoaSisErgonDTO(
    Long id,
    String nome,
    String cpf,
    String pisPasep,
    String dataNascimento,
    String sexo,
    String raca,
    String estadoCivil,
    String nacionalidade,
    String escolaridade,
    List<VinculoSisErgonDTO> vinculos
) {}
