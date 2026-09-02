# SDD Specification: Guia do Agente - Fase 1 (IAM, Segurança & Sincronização Ergon)

Este documento descreve o roteiro de execução técnica, o protocolo de trabalho e a lista de tarefas passo a passo que o agente de desenvolvimento deve seguir para implementar a **Fase 1 (Fundação do Sistema, Identity and Access Management e Sincronização Ergon)** do SIGPLANO da SEDUC.

---

## 1. Protocolo de Trabalho do Agente

Antes de digitar qualquer linha de código ou executar scripts, o agente de desenvolvimento deve seguir rigorosamente as regras abaixo:

1.  **Montar o Plano de Execução Detalhado:** O agente deve quebrar a fase em sub-tarefas atômicas e descrever o que pretende fazer.
2.  **Pedir Autorização Formal:** Apresentar o plano de tarefas ao usuário no chat e **aguardar a aprovação/permissão de início** (STOP e aguardar confirmação).
3.  **Desenvolvimento Incremental e Modular:** Desenvolver uma tarefa de cada vez. Nunca implementar o código de ponta a ponta sem antes testar e consolidar as partes básicas.
4.  **Revisão e Auto-Crítica de Código:** Após implementar cada serviço ou migração, revisar a lógica com base nas regras de negócio (SOLID, restrições contextuais, integridade do banco).
5.  **Perguntar Sempre em Caso de Dúvida:** Diante de qualquer ambiguidade de dados (ex: como mapear determinado campo retornado pelo Ergon), parar a execução e pedir esclarecimentos.

---

## 2. Passo a Passo do Desenvolvimento (Tasks)

O escopo da Fase 1 compreende o setup do monólito modular, as tabelas de controle de acesso (IAM), a segurança contextual no Spring Boot e o worker de sincronização com o banco de referência do Ergon [13, 14].

### Task 1.1: Setup da Estrutura do Monólito Modular
*   **Ação:** Criar a estrutura de diretórios do Spring Boot usando o modelo de pacotes baseado em domínios (Modular Monolith architecture):
    ```
    com.seduc.sigplano
    ├── iam          <-- (Módulo IAM)
    ├── budget       <-- (Módulo Orçamento)
    ├── planning     <-- (Módulo Planejamento)
    ├── workflow     <-- (Módulo Workflow/Engine)
    └── shared       <-- (Abstrações, DTOs e Classes utilitárias compartilhadas)
    ```
*   **Validação:** Executar o build do projeto (Maven/Gradle) garantindo que a aplicação Spring Boot inicializa sem erros na porta padrão.

### Task 1.2: Modelagem das Entidades IAM no MySQL
*   **Ação:** Criar as tabelas de banco de dados por meio de scripts de migração (Flyway/Liquibase ou DDL direto se necessário) mapeando as tabelas e relacionamentos da `Relação de dados.pdf` [13, 14]:
    *   `Pessoa`: `id`, `nome`, `email`, `cpf`, `data_nascimento`, `status` [13].
    *   `Usuario`: `id`, `login` (email/cpf), `senha` (criptografada), `pessoa_id` (nullable), `primeiro_acesso` (boolean) [13].
    *   `Roles`: `id`, `nome` (ex: `ROLE_DEMANDANTE`, `ROLE_PLANEJAMENTO`, `ROLE_ORCAMENTO`, `ROLE_GESTOR`), `descricao` [14].
    *   `Superintendências`: `id`, `sigla`, `nome`, `descricao`, `ativo` [11, 12].
    *   `Técnicos`: `id`, `usuario_id` (1:1), `superintendencia_id` (N:1), `role_id` (1:N), `ativo`, `status` [14, 15].
    *   `Vínculos Atuais` (Ergon sync target): `pessoa_id` (1:1), `numero_funcional`, `situacao_vinculo`, `setor_sigla`, `setor_nome`, `cargo`, `jornada_de_trabalho` [14].
*   **Validação:** Verificar se o banco de dados MySQL gera o esquema físico correto com todas as restrições de chaves estrangeiras (`FK`) ativas.

### Task 1.3: Implementação de Autenticação e Segurança (Spring Security)
*   **Ação:** Configurar o Spring Security com autenticação sem estado (Stateless JWT):
    *   Implementar endpoint `POST /api/auth/login`.
    *   Gerar Token JWT contendo as `Roles` do usuário técnico e o identificador do seu vínculo organizacional (`superintendencia_id`).
*   **Validação:** Escrever testes de integração para simular o login de um usuário e verificar a geração e expiração do JWT com as claims mapeadas.

### Task 1.4: Filtro de Segurança Contextual (Segurança dos Dados)
*   **Ação:** Implementar um mecanismo de controle de acesso baseado em dados (Data-Level Security):
    *   Se o usuário logado possuir a `ROLE_DEMANDANTE`, o sistema deve injetar de forma transparente a condição SQL `superintendencia_id = :userSuperintendenciaId` nas entidades operacionais (`Ações`/Atividades).
    *   Isso pode ser feito via anotação `@Filter` do Hibernate configurada no Spring Security Context, ou por meio de especificações de consulta JPA dinâmicas.
*   **Validação:** Tentar buscar registros de uma superintendência "B" usando um token autenticado da superintendência "A". O sistema deve retornar apenas os dados da própria superintendência de vínculo do usuário.

### Task 1.5: Worker de Sincronização com o Sistema Ergon
*   **Ação:** Criar o serviço de sincronização automática com os registros funcionais do Ergon [13, 14]:
    *   Implementar `ErgonSyncService` que consome as tabelas de vínculo para importar servidores ativos da SEDUC [13, 14].
    *   Sempre que um usuário realizar o primeiro acesso (`primeiro_acesso = true`), buscar seus dados no Ergon usando o CPF/E-mail [13], criar o registro na tabela `Pessoa` e `Técnicos`, associar sua `Superintendência` de acordo com a sigla do setor importada e atribuir a role `ROLE_DEMANDANTE` [13, 14, 15].
    *   Criar um componente `@Scheduled` para atualizar os metadados de lotação de forma periódica (`ultima_sincronizacao_sis_ergon`) [13, 14].
*   **Validação:** Escrever um teste unitário simulando um servidor novo do Ergon entrando no sistema pela primeira vez. Garantir que as entidades são persistidas de forma integrada, mantendo o vínculo setorial correto no banco.

---

## 3. Lista de Perguntas Técnicas Pendentes para Stakeholders

O agente deve validar com o usuário estas questões de infraestrutura antes de concluir as integrações da Fase 1:
1.  **Ambiente Ergon:** Como os dados de lotação do Ergon serão acessados na prática? Será via uma View de leitura direta no banco do Estado, por meio de uma API REST de integração, ou por carga manual programada em arquivos `.csv`?
2.  **Gestão de Perfis Especiais:** Como o sistema tratará técnicos que possuem cargos de chefia acumulados em mais de uma superintendência ou que exercem dupla função? Será permitida a troca de escopo/perfil (Context Switch) em tempo de execução?
