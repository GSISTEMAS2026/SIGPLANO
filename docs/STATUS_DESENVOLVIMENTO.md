# Documentação de Desenvolvimento - SIGPLANO

Documento único de contexto do projeto: o que é o SIGPLANO, o que já foi implementado, as regras de negócio vigentes, as convenções de engenharia do repositório e os próximos passos. Substitui os documentos anteriores (`00-analise-e-planejamento.md`, `sdd-spec-contexto.md`, `sdd-spec-fase1.md`, `claude-project-context.md`, `duvidas-pendentes.md`, `proposta-modelo-iam-conta-setor.md`), que foram consolidados aqui e removidos para eliminar contexto duplicado/desatualizado. Última atualização: 2026-09-04.

> **Aviso de Arquitetura:** todo o processo de desenvolvimento, refatoração e padronização arquitetural usa o projeto legado **SCGO** como base de conhecimento e fonte de código (referência de boas práticas) — não é para copiar às cegas, e sim adaptar ao domínio do SIGPLANO.

---

## 1. O que é o SIGPLANO

O **SIGPLANO** (Sistema de Planejamento e Orçamento Integrado da SEDUC-TO) é uma plataforma corporativa da Secretaria de Estado da Educação do Tocantins que unifica o **planejamento qualitativo** de ações/atividades com o **controle orçamentário quantitativo**, substituindo o trâmite manual de planilhas e memorandos por um fluxo de trabalho estruturado, auditável e com segregação de responsabilidades entre 4 roles — todas **contas de setor**, não perfis individuais:

- **SUPERINTENDENCIA** (uma conta por superintendência real): cadastra atividades/ações, só vê/edita os próprios dados.
- **PLANEJAMENTO:** valida o mérito qualitativo (PPA, metas físicas, termos de compromisso) de todas as superintendências — nunca vê valores/campos financeiros.
- **ORCAMENTO:** valida a viabilidade financeira (teto, fonte, natureza contábil) de todas as superintendências; também absorve as funcionalidades que antes seriam de um "Gestor" (recursos livres, transferências, excesso de arrecadação).
- **ADMIN:** acesso total ao sistema, mas fora do fluxo real de negócio — uso interno/suporte/teste. Cria contas de setor e vincula responsáveis.

### Stack técnica
- **Backend:** Java 21 + Spring Boot 4.1.1, **Monólito Modular** (pacotes por domínio, não microserviços).
- **Frontend:** React (SPA) — desenvolvimento à parte, só começa quando a API estiver parcial e documentada; sem Design System definido ainda.
- **Persistência:** MySQL (dados transacionais/ACID) + MinIO/S3 (binários — PDFs de minutas, termos, pareceres), referenciados por uma tabela `documento` central. Exclusão sempre lógica (soft delete) — nunca remoção física, nem no banco nem no storage, por auditoria.
- **Autenticação:** JWT stateless (access token 1h) + Refresh Token opaco (hash SHA-256, cookie `HttpOnly`, rotação automática a cada uso).
- **Integração externa:** SisErgon (API legada do Estado, via Feign, sempre JSON) para validar servidores por CPF e extrair lotação institucional.
- **Deploy (futuro, não é prioridade atual):** Docker + CI/CD via GitHub Actions.

### Módulos (DDD dentro do monólito)

| Pacote | Responsabilidade |
|---|---|
| `iam` | `Usuario` (a própria conta de setor, role como enum), `Pessoa`, `UsuarioResponsavel` (histórico de responsáveis), `RefreshToken`, sincronização com SisErgon, autenticação/segurança |
| `budget` (futuro) | `Ciclo`, `Fontes`, `marcadorFonte`, `Teto`, `Orçamentos` — validação de tetos financeiros |
| `planning` (futuro) | `Ações`/Atividades, descrição física, termos de compromisso, naturezas de despesa |
| `workflow` (futuro) | Máquina de estados, histórico de tramitação, Bypass Orçamentário |
| `shared` | DTOs/config/exceções/segurança/entidades base reutilizáveis por todos os módulos |

---

## 2. O que já foi implementado (Fase 1 — IAM & Segurança)

### 2.1. Infraestrutura e padrões (baseados no SCGO)
- **Tratamento Global de Exceções (`GlobalExceptionHandler`):** captura exceções da aplicação (validação, credenciais inválidas, falhas no Feign, erros internos) e traduz para respostas HTTP padronizadas; oculta *stack trace* em produção.
- **Respostas Padronizadas (`ResponseFactory`/`ApiResponseDTO`):** todo endpoint retorna um envelope JSON com `success`, `status`, `message`, `timestamp`, `data`.
- **Documentação Swagger segregada:** contratos ficam em `<módulo>/controller/docs/` como interfaces (ex.: `AutenticacaoControllerDocs`), implementadas pelo Controller real — a anotação nunca polui o Controller.
- **Logs estruturados (`logback-spring.xml`):** rotação diária, logs gerais (`sigplano.log`) separados dos de erro (`sigplano-errors.log`); pasta `logs/` e `*.log` no `.gitignore`.
- **Auditoria e deleção lógica:** `BaseEntity` com `@SQLDelete` (soft delete) e JPA Auditing (`@CreatedBy`/`@LastModifiedBy`, fallback "SISTEMA" em rotinas autônomas).
- **Profiles de ambiente:** `application-dev.yml`/`application-prod.yml`, `spring.profiles.active` padrão `dev`. Controlam `show-sql`/`format_sql` e o `secure` do cookie de refresh token.

### 2.2. Módulo IAM
- **Entidades:** `Usuario` (login, senha, role — sem tabela `Role`, é `enum`), `Pessoa` (dado sincronizado do Ergon), `UsuarioResponsavel` (histórico de responsáveis), `RefreshToken`. Não existe entidade `Diretoria`/`Superintendencia` separada — cada superintendência real é uma conta `Usuario` com role `SUPERINTENDENCIA`.
- **Tokens (JWT + Opaque Refresh Token):** Access Token JWT (1h, claim `role`); Refresh Token opaco (64 bytes, hash SHA-256 no banco, cookie `HttpOnly`, 2h, rotação automática a cada uso). Logout revoga todos os refresh tokens do usuário. Limpeza agendada diária (3h) remove tokens expirados **e** revogados.
- **Recuperação de senha:** e-mail (SMTP Gmail) com JWT de recuperação válido por 15 minutos.
- **Autorização por método:** `@PreAuthorize`/`@EnableMethodSecurity` habilitados — criação de conta de setor e vínculo de responsável são restritos a `ADMIN`.
- **`UsuarioAutenticado`** (`shared/security`): `UserDetails` customizado que carrega `id`+`role` direto no principal do `SecurityContext`, evitando round-trip ao banco para saber quem está logado — base para o Data-Level Security futuro (seção 4).

### 2.3. Integração com SisErgon
- **Feign Client (`SisErgonConsumer`):** consome a API legada do Estado para validar servidores (CPF, data de nascimento, vínculo funcional) — sempre via API/JSON, pelos dois métodos já existentes; não há view de banco nem carga `.csv`.
- **Gerenciamento autônomo de sessão (`SisErgonTokenConfig`):** login agendado (`@Scheduled`) no SisErgon, mantendo o token de serviço sempre válido.

---

## 3. Regras de negócio vigentes (IAM)

1. **Sem autocadastro.** Só `ADMIN` cria contas de setor e vincula responsáveis.
2. **Login sempre por e-mail institucional.** Toda conta de setor (`Usuario`) autentica com e-mail `@seduc.to.gov.br` + senha. **Não existe login por CPF.**
3. **4 roles, todas de conta de setor:** `SUPERINTENDENCIA`, `PLANEJAMENTO`, `ORCAMENTO`, `ADMIN`. Pode existir mais de uma conta `SUPERINTENDENCIA` (uma por superintendência real); `PLANEJAMENTO`/`ORCAMENTO`/`ADMIN` tendem a ser contas únicas, sem restrição de unicidade no schema.
4. **Responsável pelo acesso (opcional, histórico nunca sobrescrito):**
   - Toda conta de setor **pode** ter um responsável — pessoa física validada via SisErgon (CPF + data de nascimento + vínculo ativo mais recente por `dataAdmissao`; bloqueia se `situacaoVinculo` contiver "DESATIVADO"/"EXONERADO").
   - Trocar o responsável **nunca sobrescreve**: o vínculo antigo é desativado (`ativo=false`) e um novo é inserido — histórico preservado com as datas de auditoria do `BaseEntity`.
   - Não é obrigatório: a conta funciona normalmente por e-mail/senha mesmo sem responsável vinculado.
5. **Fluxo de primeiro acesso:** toda conta de setor nasce com `primeiroAcesso = true` e a senha definida no cadastro. Após o primeiro login, a API exige redefinição de senha; a nova senha não pode repetir a provisória.
6. **Funcionalidades de "Gestor":** absorvidas pela role `ORCAMENTO` — não existe role `GESTOR`. `ADMIN` tem acesso total, mas fora do fluxo real de negócio.

---

## 4. Isolamento de dados (Data-Level Security) — decisão registrada, aplicação pendente de `planning`/`budget`

Regra de negócio: `SUPERINTENDENCIA` só vê os próprios registros; `ORCAMENTO`, `PLANEJAMENTO` e `ADMIN` veem de todas as superintendências. Adicionalmente, `PLANEJAMENTO` nunca vê campos de valor/financeiro — isso é uma restrição de **coluna**, não de linha, e não se resolve com o mesmo mecanismo do escopo de linha.

**Mecanismo escolhido:** `Specification` explícita — **não** `@Filter` do Hibernate (que era a ideia original). Motivo: `@Filter` falha **aberto** — se alguém esquecer de habilitar o filtro numa rota nova ou numa rotina `@Scheduled`, a query roda sem filtro nenhum e vaza dado entre setores. `Specification` explícita falha **fechado** e é testável com Mockito puro, como o resto do projeto.

**Duas camadas, a implementar quando `Ação`/`planning` existirem:**

1. **Escopo de linha** — `Specification` central, aplicada só para `SUPERINTENDENCIA`:
   ```java
   public class EscopoSetorSpecification {
       public static <T> Specification<T> aplicar(UsuarioAutenticado usuarioLogado) {
           if (usuarioLogado.getRole() == Role.SUPERINTENDENCIA) {
               return (root, query, cb) -> cb.equal(root.get("usuarioSetor").get("id"), usuarioLogado.getId());
           }
           return Specification.where(null); // ORCAMENTO, PLANEJAMENTO, ADMIN: sem restrição de linha
       }
   }
   ```
2. **Escopo de coluna** — a entidade nunca sai crua de um Controller; cada perfil recebe seu DTO de resposta (o DTO servido ao Planejamento simplesmente não declara o campo `valor` — erro de compilação impede vazar por acidente).

A peça de apoio (`UsuarioAutenticado`, seção 2.2) já está pronta; falta só a `Specification` real com o nome de campo verdadeiro de `Ação` e os DTOs por perfil, quando esses módulos começarem.

---

## 5. Regras de negócio já especificadas para Fases futuras (orçamento/planejamento)

Levantamento original de arquitetura para os módulos `budget`/`planning`/`workflow`, ainda válido como referência — nada disso foi implementado ainda.

### 5.1. Princípios de negócio
- **Indivisibilidade de Ações Prioritárias:** ações marcadas como prioridade de Governo (LDO) ou de Gestão não podem sofrer redução de saldo ou redistribuição — apenas suplementação.
- **Controle de Fontes e Marcadores:** codificação orçamentária no padrão `500.xxxx.xxx` (3 dígitos de Fonte + marcadores). Saldos geridos de forma agregada e individualizada por marcador.
- **Validação segregada:** Planejamento valida mérito estratégico/qualitativo e termos de compromisso (MEC/FNDE) sem interagir com valores; Orçamento controla tetos financeiros e conformidade contábil.
- **Controle de saldo impeditivo:** o sistema deve barrar fisicamente o envio caso a soma das metas financeiras ultrapasse o teto do grupo de despesa da ação; não há saldo negativo transitório.

### 5.2. Armazenamento de documentos (MySQL x MinIO)
Cada arquivo anexado é referenciado pelas tabelas operacionais via `id` (UUID) de uma tabela centralizadora `documento`; o binário fica no bucket `sigplano-arquivos` no MinIO, caminho em `storage_key`. Exclusão sempre lógica (ver seção 1).

### 5.3. Máquina de estados e Bypass Otimizado
Ciclo de vida de uma atividade submetida por uma conta `SUPERINTENDENCIA`:
1. **RASCUNHO:** edição livre pela superintendência.
2. **PENDENTE_PLANEJAMENTO:** bloqueado para a superintendência; aguardando validação de mérito/termos pelo Planejamento.
3. **PENDENTE_ORCAMENTO:** bloqueado para o Planejamento; aguardando validação de teto/natureza contábil pelo Orçamento.
4. **AJUSTE_CONTABIL_BYPASS:** acionado se o Orçamento rejeitar por erro de natureza/valor. O formulário da superintendência desbloqueia **apenas** para campos contábeis/financeiros — descrição, PPA e metas físicas ficam bloqueados para leitura. Após re-submissão, salta direto para `PENDENTE_ORCAMENTO`, sem nova validação do Planejamento (o Bypass).
5. **APROVADO/DD_EFETUADO:** o Orçamento efetua o registro; a partir daí é **estritamente imutável** para qualquer ator.

### 5.4. Recursos Livres, Transferências e Excesso de Arrecadação (agora sob `ORCAMENTO`)
- **Recursos Livres:** visualização de saldos de cotas não detalhadas (pré-DD).
- **Transferências:** remanejamento de saldos livres entre superintendências, sob validação prévia.
- **Excesso de Arrecadação:** lançamentos excepcionais de fim de ano fora da LOA/PPA regular (setembro/outubro).

### 5.5. Princípios SOLID a aplicar nesses módulos
- **S:** separar validadores qualitativos (`QualitativeValidator`) dos financeiros (`FinancialValidator`).
- **O:** regras de ações prioritárias (LDO/Gestão) via composição/herança (`IBudgetRule`), extensíveis sem modificar o core.
- **L:** fontes gerais e marcadores sob o mesmo contrato de verificação de saldo (`AbstractResourceSource`).
- **I:** segregar interfaces de repositório/serviço entre planejamento e orçamento (`IQualitativeApprovable` vs `IFinancialApprovable`).
- **D:** integrações externas (SIAFE, Cfaz, Ergon) por trás de interfaces/gateways no domínio, implementação concreta isolada na infraestrutura.

---

## 6. Convenções de engenharia deste repositório

- **Nomenclatura de domínio em pt-BR** (`buscarPorSuperintendenciaECicloAtivo`, `validarSaldoDisponivel`); termos de framework seguem convenção Java/Spring padrão.
- **Toda resposta de Controller** passa por `ResponseFactory`/`ApiResponseDTO`.
- **Swagger/OpenAPI segregado** em `<módulo>/controller/docs/` (interfaces `*ControllerDocs`) — nunca anotar o Controller diretamente.
- **Exceções centralizadas** em `GlobalExceptionHandler`; mensagens amigáveis, sem stack trace em produção.
- **Soft delete + auditoria** via `BaseEntity` — toda entidade de domínio operacional deve estendê-la. Exceção deliberada: `UsuarioResponsavel` não usa `@SQLDelete`/`@SQLRestriction`, porque precisa manter o histórico completo sempre consultável.
- **Protocolo de trabalho do agente** (`.claude/skills/backend-rules.md`): mapear contexto → auditar reuso (DRY) → apresentar relatório de viabilidade (objetivo, arquivos impactados, reuso, riscos, dúvidas) → aguardar aprovação antes de codar → implementar de forma incremental → gerar testes → resumir o que foi entregue.
- **Dados sensíveis (CPF, dados funcionais):** cautela extra — evitar logs com dados pessoais, nunca commitar segredos (`.env`/`*.env` no `.gitignore`), cuidado com seeds de usuário admin em qualquer ambiente que não seja `dev` (`DataLoaderRunner` é `@Profile("dev")`).

---

## 7. Próximos passos

### Fase 2: Módulos Core (`budget` e `planning`)
- Estruturação das pastas de domínio (`Ação`, `Ciclo`, `Fontes`, `Teto`, `Orçamentos`, `Documento`) conforme seção 5.
- Aplicação efetiva do Data-Level Security da seção 4 assim que `Ação` existir.

### Fase 3: Regras de negócio orçamentárias
- Ciclo de vida dos orçamentos, validações de teto, fluxos de aprovação e workflow de tramitação (máquina de estados da seção 5.3).
- Expansão dos endpoints seguindo o padrão já estabelecido de documentação e tratamento de erros.

---

## 8. Perguntas em aberto para stakeholders

- **Design System do frontend:** padrão visual definido pela SEDUC, ou biblioteca de mercado (Material-UI, Ant Design)? Não bloqueia o backend.

---

## 9. Dívida técnica conhecida

- `SigplanoBackendApplicationTests.contextLoads` falha por incompatibilidade entre `spring-cloud-dependencies:2024.0.0` e `spring-boot-starter-parent:4.1.1` (`ClassNotFoundException: ServerProperties`). Confirmado pré-existente (reproduzido com `git stash`, antes do refactor de IAM desta sessão) — sem relação com o módulo `iam`. Não corrigido ainda; próximo item ao mexer em dependências do `pom.xml`.

---

## 10. Histórico de decisões relevantes

- **Modelo de Roles/Contas (mudança estrutural):** o modelo original cogitava `Usuario` como Técnico (login=CPF) ou Setor Institucional (login=e-mail), com `Role` como tabela e entidades `Superintendencia`/`Diretoria`/`Técnicos Roles` separadas. Foi substituído pelo modelo atual (seções 1–3): login sempre por e-mail, `Role` como `enum`, sem `Diretoria`, responsável pelo acesso rastreado à parte via `UsuarioResponsavel`.
- **Vínculo acumulado:** um usuário nunca tem duas roles nem pertence a duas superintendências ao mesmo tempo — não existe seletor de contexto/perfil ativo.
- **Data-Level Security:** decidido usar `Specification` em vez de `@Filter` do Hibernate (seção 4), revertendo a sugestão original — motivo de segurança (fail-closed vs. fail-open).
- **`dev.env.example`:** arquivo pessoal de um colega (VS Code), não é fonte de verdade para variáveis de ambiente do projeto.
