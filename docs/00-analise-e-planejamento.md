# Análise Geral e Planejamento - SIGPLANO

## 1. Contexto do Projeto (O que o projeto deve ser)
O **SIGPLANO** (Sistema de Planejamento e Orçamento Integrado da SEDUC) é uma plataforma corporativa voltada para a gestão pública. O objetivo central é **unificar o planejamento qualitativo e o controle orçamentário quantitativo**, eliminando a burocracia de planilhas e memorandos manuais. 

O sistema orquestra um fluxo de trabalho estruturado, onde:
- Demandantes (Superintendências) cadastram atividades.
- O Planejamento valida o mérito/qualidade (sem mexer em valores).
- O Orçamento valida a viabilidade financeira (teto, contabilidade).
- Ações são categorizadas rigorosamente (regras específicas para prioridades do governo).
- Há separação estrita de escopo (IAM/Segurança) garantindo que um demandante só veja/edite dados de sua superintendência.

## 2. Análise de Viabilidade (O que é viável)
As decisões de arquitetura mapeadas no documento `sdd-spec-contexto.md` são altamente coerentes, viáveis e alinhadas com as melhores práticas da indústria:
*   **Monólito Modular (Spring Boot):** Perfeito para a realidade do projeto. Evita a complexidade de rede de microserviços, mas mantém a separação lógica de domínios (IAM, Budget, Planning, Workflow), facilitando manutenção.
*   **Armazenamento Híbrido (MySQL + MinIO):** O uso de MinIO (Object Storage) para PDFs desafoga o banco relacional, garantindo transações financeiras rápidas e seguras no MySQL sem inchaço no banco de dados.
*   **Máquina de Estados e Bypass:** A regra de pular o planejamento em ajustes puramente financeiros (Bypass) traz otimização ao processo e é perfeitamente modelável via *State Pattern* ou tabelas de status no banco.
*   **Segurança de Dados via Hibernate `@Filter`:** Usar filtros de interceptação para garantir que uma Superintendência não acesse dados da outra é muito mais seguro e manutenível do que espalhar `WHERE id_superintendencia = X` por todas as queries do sistema.
*   **Autenticação JWT Stateless:** Totalmente padrão para integrações com Frontends em React (SPA).

## 3. O que pode ser trabalhado (Próximos Passos de Execução)
Com base na **Fase 1**, o trabalho inicial pode ser iniciado imediatamente com as seguintes frentes:
1.  **Setup de Infraestrutura de Desenvolvimento:** Criar a base do Spring Boot (Java 17+) com as dependências do pom.xml (Web, Data JPA, Security, Flyway/Liquibase, MySQL, MinIO SDK).
2.  **Estruturação de Pacotes (DDD):** Montar a estrutura `iam`, `budget`, `planning`, `workflow`, `shared`.
3.  **Modelagem e Migrações (IAM):** Criar as migrações (SQL) iniciais para as tabelas: `Pessoa`, `Usuario`, `Roles`, `Superintendencia`, `Tecnicos` e `Vinculos`.
4.  **Implementação de Segurança Base:** Configurar o Spring Security, gerar token JWT com as `Roles` e implementar os filtros iniciais.

## 4. Dúvidas e Pontos de Atenção (Gaps para Investigação)
Para garantir que não haja retrabalho ou bloqueios, as seguintes dúvidas precisam ser esclarecidas junto aos Stakeholders (ou definirmos premissas técnicas):

*   **Sincronização Ergon:** Qual o formato de leitura dos dados do sistema Ergon? Será via conexão direta de leitura (View) num banco externo, consumo de uma API REST, ou upload automático/manual de arquivos `.csv`?
*   **Acúmulo de Cargos/Perfis:** Como o sistema deve reagir se um servidor possui dois vínculos ativos (ex: responde por duas superintendências)? O JWT trará as duas ou haverá um seletor de "perfil ativo" no login?
*   **Frontend / UI:** Já existe algum padrão visual ou Design System definido pela SEDUC ou usaremos bibliotecas padrão do React (ex: Material-UI, Ant Design)?
*   **Integração MinIO x Banco:** Na exclusão lógica de um documento no MySQL, o arquivo no MinIO deve ser purgado imediatamente ou mantido em *Cold Storage* para auditoria?
*   **Ambiente de Deploy:** O sistema rodará em servidores on-premise do Estado ou em nuvem? Haverá uso de Docker/Kubernetes? (Isso afeta como configuramos as variáveis de ambiente `dev.env`).
