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

3. Register the sample database, using its read-only `askdb_reader` user:

   ```sh
   curl -X POST localhost:8080/api/connections -H 'Content-Type: application/json' \
     -d '{"username":"askdb_reader","password":"askdb_reader","url":"jdbc:postgresql://localhost:5433/pagila","type":"postgresql"}'
   ```

4. Ask a question, using the `id` returned above:

   ```sh
   curl -X POST localhost:8080/api/connections/1/queries -H 'Content-Type: application/json' \
     -d '{"question":"Which 5 customers spent the most in total?"}'
   ```

   The response has the answer, the SQL it was based on, the result rows, and a `queryId` for its history entry.

## Query history

Every question is saved, whether it succeeds or fails, to help debug wrong answers, find test cases and track cost.

| Endpoint | Returns |
| --- | --- |
| `GET /api/connections/{id}/queries?page=0&size=20` | A connection's questions, newest first, paginated (default 20 per page) |
| `GET /api/queries/{queryId}` | One question, even if its connection has since been deleted |

Each entry records the question, status (`SUCCEEDED` or `FAILED`), the answer, the final SQL and its row count, every SQL attempt the model made (including failed ones it then corrected), any error message, the model used, input and output tokens, duration and time asked.

Result rows are deliberately **not** stored: they're copies of data from the queried database, and the SQL is enough to re-run a query.

## Security

SQL written by the model is treated as untrusted. Every query runs behind these safeguards:

- **SELECT only:** queries must start with `SELECT` or `WITH`.
- **One statement:** queries run as prepared statements, which PostgreSQL limits to a single statement.
- **Read-only:** each query runs in a read-only transaction that is always rolled back, so the database rejects any write.
- **Limits:** queries are cancelled after 10 seconds, and at most 100 rows are returned.
- **No credentials sent to the model:** Claude only sees the schema and query results, never the connection details.

**Register connections with a read-only database user.** AskDB doesn't check a registered user's privileges. The safeguards above block writes, but they don't limit what can be *read*: the model can query anything the user can access, and superusers can even read server files through SQL functions. Create a user with `SELECT` access to only the data you want to be queryable. The Pagila sample includes one, `askdb_reader`.

Not yet in place: authentication on the API, encryption of stored connection passwords, and restrictions on which hosts connections may point to. These are planned for the AWS deployment.

## Tests

`./mvnw test` runs integration tests against real PostgreSQL containers (Testcontainers). A scripted fake model stands in for Claude, so the full question flow is tested without an API key, at no cost, with the same result every run.

## Evals

The tests above prove the code works; the eval measures whether Claude's **answers** are right. It asks AskDB 34 questions about the [Pagila](https://github.com/devrimgunduz/pagila) sample database (`src/test/resources/evals/pagila/cases.yaml`) and grades each answer two ways:

- **answer_correct**: an LLM judge (Claude Sonnet 5.5) reads the question, a rubric, the reference query result and AskDB's answer, and decides pass or fail.
- **sql_result_match**: a code check that the reference values appear in the rows of the last query AskDB ran. A diagnostic only: it fails correct answers whose last query was a different one.

Cases cover lookups, aggregation, joins, dates, ties in rankings, ambiguous questions, large results, questions the data can't answer, and requests to change data, which must be refused.

The eval calls the real Claude API and costs about **$1 per run**, so it's excluded from `./mvnw test`. With Pagila running (`docker compose up`) and `ANTHROPIC_API_KEY` in `.env`:

```sh
mvn test -Peval                                        # all cases
mvn test -Peval -Deval.ids=count-films,store-most-revenue   # selected cases
mvn test -Peval -Deval.variant=v1                      # a new variant, after changing a prompt or model
```

Results go to `.claude/hillclimb/ask-pagila/<variant>/results.jsonl`, one JSON row per case. A rerun skips cases already in the file, so delete the variant's folder to grade from scratch (for example, after changing the judge). The eval runs in UTC, because Postgres sessions use the JVM's timezone and that decides what "March 2022" means.
