# Backend do LotoLab

Java 21, Spring Boot 4.1.1, Maven, PostgreSQL, Flyway e JDBC/JdbcTemplate. O backend inclui API, aplicação, domínio, persistência e os módulos contests e research; não utiliza JPA/Hibernate.

Consulte o README da raiz para configuração local, Compose isolado, endpoints, migrações V1–V9 e integração opcional com Gemini.

A partir da raiz:

    Set-Location backend
    .\mvnw.cmd spring-boot:run

Validação:

    .\mvnw.cmd clean verify

Os testes de integração PostgreSQL usam Testcontainers e requerem Docker.