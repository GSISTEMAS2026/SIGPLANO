# SDD Specification: Contexto Geral da Aplicação (SIGPLANO - SEDUC)

Este documento serve como a especificação de arquitetura, domínio e comportamento para o agente de desenvolvimento do **SIGPLANO** (Sistema de Planejamento e Orçamento Integrado da SEDUC - Secretaria de Estado de Educação).

---

## 1. Visão Geral e Filosofia do Sistema

O **SIGPLANO** tem como objetivo unificar o planejamento qualitativo e o controle orçamentário quantitativo da SEDUC em uma única plataforma modular [16, 17, 79]. O sistema foi concebido para eliminar processos burocráticos morosos (como o trâmite contínuo de planilhas e memorandos avulsos) [17, 42], proporcionando um fluxo de trabalho estruturado e seguro para as superintendências demandantes e as diretorias de validação [17, 25].

### Princípios de Negócio Fundamentais:
*   **Indivisibilidade de Ações Prioritárias:** Ações marcadas como prioridades de Governo (LDO) ou de Gestão não podem sofrer redução de saldo ou redistribuição descontrolada; apenas suplementação é permitida, devendo a regra de priorização ser aplicada à ação como um todo [1, 2, 3, 23, 24, 84, 85].
*   **Controle de Fontes e Marcadores (Relação 1:N):** A codificação orçamentária oficial segue o padrão `500.xxxx.xxx` (3 dígitos de Fonte e o restante como marcadores de fonte específicos) [2, 8]. O sistema gerencia saldos tanto de forma agregada quanto individualizada em marcadores [18, 60, 61, 82].
*   **Validação Segregada:** O Planejamento valida o mérito estratégico/qualitativo das atividades e termos de compromisso (MEC/FNDE) sem interagir com valores [1, 25, 40, 98], enquanto o Orçamento controla estritamente os tetos financeiros e conformidades contábeis [1, 18, 25, 40, 98].

---

## 2. Decisões de Arquitetura Técnica

Para suportar as regras complexas de integridade transacional sem introduzir complexidade operacional desnecessária na infraestrutura do Estado, adota-se a seguinte stack tecnológica:

*   **Backend:** Spring Boot (Java 17+) configurado sob o paradigma de **Monólito Modular (Modular Monolith)**.
*   **Frontend:** React (SPA) moderno para renderização dinâmica de formulários e gerenciamento de estado.
*   **Banco de Dados de Metadados:** MySQL (Banco de Dados Relacional para transações ACID de saldos, usuários e fluxos).
*   **Armazenamento de Binários (MinIO):** Object Storage local compatível com a API S3 para persistência isolada de PDFs (minutas, termos, leis e pareceres de recusa), garantindo que o MySQL permaneça leve e limpo de campos `BLOB`.

### Fronteira dos Módulos (Domínios DDD no Monólito):
1.  **IAM & Access (Modulo Azul):** Gerencia `Usuario`, `Pessoa`, `Técnicos`, `Roles` e a sincronização do `Servidor` com o sistema **Ergon** [13, 14].
2.  **Budget Core (Módulo Verde):** Controla as tabelas `Ciclo`, `Fontes`, `marcadoFonte`, `Teto` e `orcamentos` [12], executando as validações matemáticas de teto financeiro [17, 20].
3.  **Planning (Módulo Laranja):** Controla a tabela operacional `Ações` (atividades, descrição física, termos de compromisso e naturezas de despesa) [12, 13, 25].
4.  **Workflow Engine (Módulo Roxo):** Controla a máquina de estados, o histórico de tramitação e o mecanismo de **Bypass Otimizado** [6, 29].

---

## 3. Modelo Lógico de Dados e Relações

A estrutura relacional do MySQL mapeia as tabelas conforme a especificação técnica:

```
  +-------------------+       1:1       +-------------------+       1:1       +-------------------+
  |      Pessoa       |<----------------|      Usuario      |<----------------|      Técnico      |
  +-------------------+                 +-------------------+                 +-------------------+
                                                                                        | N:1
                                                                                        v
  +-------------------+       1:N       +-------------------+                 +-------------------+
  |       Roles       |---------------->|   Técnicos Roles  |                 | Superintendência  |
  +-------------------+                 +-------------------+                 +-------------------+
                                                                                        | 1:N
                                                                                        v
  +-------------------+       N:1       +-------------------+                 +-------------------+
  |   Ação Governo    |<----------------|       Ações       |---------------->|     Documento     |
  +-------------------+                 |  (Atividade/Fluxo)|                 +-------------------+
                                        +-------------------+                    (Ponteiro MySQL)
                                                                                        |
                                                                                        | (Reference UUID)
                                                                                        v
                                                                              +-------------------+
                                                                              |     MinIO S3      |
                                                                              |  (Arquivo PDF)    |
                                                                              +-------------------+
```

### Detalhe do Modelo de Documentos (MySQL x MinIO):
Para cada arquivo anexado, as tabelas operacionais do MySQL referenciam o `id` (UUID) de uma tabela centralizadora chamada `documento`. O binário real é salvo no bucket `sigplano-arquivos` no MinIO sob o caminho especificado em `storage_key`.

---

## 4. Comportamentos Críticos e Regras Sistêmicas

### A. Máquina de Estados e Bypass Otimizado:
O ciclo de vida de uma atividade submetida por uma Superintendência passa pelos seguintes status:
1.  **RASCUNHO:** Edição livre pelo demandante [75].
2.  **PENDENTE_PLANEJAMENTO:** Bloqueado para o demandante. Aguardando validação de mérito e termos pelo Planejamento [25, 26].
3.  **PENDENTE_ORCAMENTO:** Bloqueado para o Planejamento. Aguardando validação de teto e natureza contábil pelo Orçamento [28, 95].
4.  **AJUSTE_CONTABIL_BYPASS:** Acionado caso o Orçamento rejeite o fluxo por erro de natureza ou valor [6, 29, 89]. 
    *   *Regra de Bloqueio:* O formulário do demandante desbloqueia **apenas** para edição de campos contábeis/financeiros (valores, fonte, elemento). Campos de descrição, PPA e metas físicas validados anteriormente ficam **bloqueados para leitura** [1, 29, 40].
    *   *O Bypass:* Após a re-submissão pelo demandante, o registro salta diretamente para o status **PENDENTE_ORCAMENTO**, sem exigir que o Planejamento revalide o mérito [6, 29, 89].
5.  **APROVADO / DD_EFETUADO:** O Orçamento efetua o registro. A partir deste momento, o registro torna-se **estritamente Imutável (Somente Leitura)** no SIGPLANO para qualquer ator [75, 76, 125].

### B. Controle de Saldo Impeditivo:
*   O sistema impede fisicamente o envio de qualquer atividade caso a somatória das metas financeiras informadas pelo demandante ultrapasse o limite total alocado por grupo de despesa (31, 33, 44) para aquela ação específica [20, 34, 35, 36, 94].
*   Não são permitidos saldos negativos transitórios nas submissões operacionais [34, 35, 94].

### C. Módulo Gestor (Recursos Livres, Excesso e Transferências):
*   **Recursos Livres:** Espaço para o Gestor visualizar saldos de cotas não detalhadas (pré-DD) [75].
*   **Transferências:** Permite remanejar saldos livres entre superintendências sob validação prévia e aprovação da Diretoria de Orçamento [74, 77, 124, 126].
*   **Excesso de Arrecadação:** Lançamentos excepcionais de fim de ano que transitam fora da LOA/PPA regular (setembro/outubro), operados exclusivamente pelo perfil do Gestor [32, 69, 71, 121, 122].

---

## 5. Princípios SOLID Aplicados

O agente de desenvolvimento deve garantir a aderência estrita aos princípios de software limpo:
*   **S:** Separar validadores de metadados qualitativos (`QualitativeValidator`) dos validadores de conformidade financeira (`FinancialValidator`).
*   **O:** Utilizar herança/composição de regras (`IBudgetRule`) para implementar restrições de ações prioritárias (LDO/Gestão) e comuns, permitindo extensões sem modificação do core.
*   **L:** Tratar fontes gerais e marcadores sob o mesmo contrato de verificação de saldo (`AbstractResourceSource`).
*   **I:** Segregar interfaces do repositório de serviços para que o módulo de planejamento não acesse operações de orçamento e vice-versa (`IQualitativeApprovable` vs `IFinancialApprovable`).
*   **D:** Acoplar as integrações do SIAFE, Cfaz e Ergon a interfaces abstratas (Gateways) definidas no domínio, mantendo a implementação concreta isolada na camada de infraestrutura.
