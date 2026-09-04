package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VinculoSisErgonDTO(
    Long id,
    Long numeroFuncional,
    Integer numeroVinculo,
    String situacaoVinculo,
    String regional,
    String setorSigla,
    String setorNome,
    String setorMunicipio,
    String regimeJuridico,
    String tipoVinculo,
    String categoria,
    Integer codigoCargo,
    String cargoNivelReferencia,
    Integer jornadaDeTrabalho,
    String dataAdmissao
) {}
