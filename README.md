# AskDB

Ask questions about your PostgreSQL database in plain English. AskDB reads the database's schema, has Claude write and run SQL through a tool call, and returns a plain-English answer along with the SQL and result rows it was based on.

Built with Java 25, Spring Boot 4, Spring AI and PostgreSQL.

## Run locally

Requires JDK 25 and Docker.

1. Create a `.env` file in the project root (it's gitignored):

   ```
   ANTHROPIC_API_KEY=<your Anthropic API key>
   DB_PASSWORD=<any password for the local database>
   PGADMIN_EMAIL=<any email>
   PGADMIN_PASSWORD=<any password>
   ```

2. Start the app with `./mvnw spring-boot:run`. Docker Compose starts the app's database and a [Pagila](https://github.com/devrimgunduz/pagila) sample database (a DVD rental store) to ask questions about.

3. Register the sample database:

   ```sh
   curl -X POST localhost:8080/api/connections -H 'Content-Type: application/json' \
     -d '{"username":"postgres","password":"pagila","url":"jdbc:postgresql://localhost:5433/pagila","type":"postgresql"}'
   ```

4. Ask a question, using the `id` returned above:

   ```sh
   curl -X POST localhost:8080/api/connections/1/queries -H 'Content-Type: application/json' \
     -d '{"question":"Which 5 customers spent the most in total?"}'
   ```

## Tests

`./mvnw test` runs integration tests against real PostgreSQL containers (Testcontainers). They don't call Claude and need no API key.
