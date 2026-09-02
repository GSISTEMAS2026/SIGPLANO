# Documentação do Projeto - SIGPLANO

Bem-vindo à documentação técnica do **SIGPLANO** (Sistema de Planejamento e Orçamento Integrado da SEDUC).

Esta pasta contém as definições de arquitetura, domínio, fluxos e regras de negócio essenciais para a evolução e manutenção do sistema.

## Índice de Documentação

1. **[Análise e Planejamento Inicial](00-analise-e-planejamento.md)**
   - Visão geral, viabilidade, próximos passos e dúvidas arquiteturais.

2. **[Arquitetura do Sistema (Em breve)](01-arquitetura.md)**
   - Detalhamento do Monólito Modular (Spring Boot).
   - Definição dos módulos (IAM, Budget, Planning, Workflow).
   - Comunicação e separação de persistência (MySQL x MinIO).

3. **[Modelo de Dados Relacional (Em breve)](02-modelo-de-dados.md)**
   - Estrutura de tabelas e relacionamentos.
   - Domínios de Acesso (IAM), Ações (Planejamento) e Limites (Orçamento).

4. **[Máquina de Estados e Fluxos de Trabalho (Em breve)](03-fluxos-de-estado.md)**
   - Ciclo de vida das Ações/Atividades.
   - Bypass Orçamentário e bloqueios seletivos de edição.

5. **[Segurança e Integrações (Em breve)](04-seguranca-e-integracoes.md)**
   - IAM, JWT.
   - Regras de sincronização funcional com o sistema **Ergon**.
