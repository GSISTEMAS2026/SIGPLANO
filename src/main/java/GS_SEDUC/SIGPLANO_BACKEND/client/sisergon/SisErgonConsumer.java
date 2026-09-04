package GS_SEDUC.SIGPLANO_BACKEND.client.sisergon;

import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.response.LoginResponseDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.PessoaSisErgonDTO;
import GS_SEDUC.SIGPLANO_BACKEND.client.sisergon.dto.request.SisErgonLoginRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "sisErgonConsumer", url = "${app.sisergon.url}", configuration = FeignConfig.class)
public interface SisErgonConsumer {

    @PostMapping("/auth/login")
    LoginResponseDTO login(@RequestBody SisErgonLoginRequestDTO requestDTO);

    @GetMapping("/pessoa/v1/cpf/{cpf}")
    ResponseEntity<PessoaSisErgonDTO> findPessoaByCpf(@PathVariable String cpf);
}
