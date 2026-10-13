# AskDB

Ask questions about your PostgreSQL database in plain English. AskDB reads the database's schema, has Claude write and run SQL through a tool call, and returns a plain-English answer along with the SQL and result rows it was based on.

Built with Java 25, Spring Boot 4, Spring AI and PostgreSQL.

## Run locally

Requires JDK 25, Docker, and an [Amazon Cognito](https://aws.amazon.com/cognito/) user pool to sign in with (see [Authentication](#authentication)).

1. Create a `.env` file in the project root (it's gitignored):

   ```
   ANTHROPIC_API_KEY=<your Anthropic API key>
   ASKDB_ENCRYPTION_KEY=<output of: openssl rand -base64 32>
   COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<user pool ID>
   COGNITO_CLIENT_ID=<app client ID>
   DB_PASSWORD=<any password for the local database>
   PGADMIN_EMAIL=<any email>
   PGADMIN_PASSWORD=<any password>
   ```

   `ASKDB_ENCRYPTION_KEY` encrypts the connection passwords AskDB stores. Keep it safe: if it's lost or changed, stored connections can't be read and must be registered again.

2. Start the app with `./mvnw spring-boot:run`. Docker Compose starts the app's database and a [Pagila](https://github.com/devrimgunduz/pagila) sample database (a DVD rental store) to ask questions about.

3. Get an access token (see [Authentication](#authentication)) and keep it in `TOKEN` for the requests below.

4. Register the sample database, using its read-only `askdb_reader` user:

   ```sh
   curl -X POST localhost:8080/api/connections -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"username":"askdb_reader","password":"askdb_reader","url":"jdbc:postgresql://localhost:5433/pagila","type":"postgresql"}'
   ```

5. Ask a question, using the `id` returned above:

   ```sh
   curl -X POST localhost:8080/api/connections/1/queries -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"question":"Which 5 customers spent the most in total?"}'
   ```

   The response has the answer, the SQL it was based on, the result rows, and a `queryId` for its history entry.

## Authentication

Every API request needs a Cognito **access token** in an `Authorization: Bearer` header; without a valid one the API returns 401. AskDB only checks tokens, so sign-up and sign-in are Cognito's job.

One-time setup in the AWS console:

1. Create a Cognito **user pool** (the Lite tier is free for up to 10,000 monthly active users). Not an identity pool: AskDB needs tokens, not AWS credentials.
2. Add a public app client (no client secret) with the `ALLOW_USER_PASSWORD_AUTH` flow enabled.
3. Create a user. Users created in the console get a temporary password, so make it permanent:

   ```sh
   aws cognito-idp admin-set-user-password --user-pool-id <user pool ID> --username <email> --password '<new password>' --permanent
   ```

4. Put the pool's issuer URI and the app client ID in `.env` (see [Run locally](#run-locally)). The issuer URI is the pool's token signing key URL without `/.well-known/jwks.json`.

To get a token (it lasts 60 minutes):

```sh
TOKEN=$(aws cognito-idp initiate-auth --region <region> --client-id <app client ID> \
  --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME=<email>,PASSWORD='<password>' \
  --query AuthenticationResult.AccessToken --output text)
```

Use the `AccessToken`, not the `IdToken`: AskDB rejects ID tokens.

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

Every request must carry a valid Cognito **access token**. Spring Security checks its signature against the user pool's public keys, its issuer and its expiry. Cognito access tokens have no audience (`aud`) claim, so AskDB also checks that `token_use` is `access` and `client_id` is AskDB's app client: an ID token, or a token issued to another app in the same pool, is rejected. The API is stateless, with no sessions or cookies, so CSRF protection is off.

Stored connection passwords are **encrypted at rest** with AES-256-GCM, using a key kept outside the code and the database (`ASKDB_ENCRYPTION_KEY`). A leaked database alone doesn't expose them, and a tampered value fails to decrypt instead of being used. They're never returned by the API or written to logs.

**Register connections with a read-only database user.** AskDB doesn't check a registered user's privileges. The safeguards above block writes, but they don't limit what can be *read*: the model can query anything the user can access, and superusers can even read server files through SQL functions. Create a user with `SELECT` access to only the data you want to be queryable. The Pagila sample includes one, `askdb_reader`.

Not yet in place: connections belong to no one yet, so any signed-in user can use any connection, and there are no restrictions on which hosts connections may point to. Both are planned for the AWS deployment.

## Tests

`./mvnw test` runs integration tests against real PostgreSQL containers (Testcontainers). A scripted fake model stands in for Claude, so the full question flow is tested without an API key, at no cost, with the same result every run. Requests sign in with a mock JWT, so the tests don't need Cognito.

## Evals

The tests above prove the code works; the eval measures whether Claude's **answers** are right. It asks AskDB 34 questions about the [Pagila](https://github.com/devrimgunduz/pagila) sample database (`src/test/resources/evals/pagila/cases.yaml`) and grades each answer two ways:

- **answer_correct**: an LLM judge (Claude Sonnet 5.5) reads the question, a rubric, the reference query result and AskDB's answer, and decides pass or fail.
- **sql_result_match**: a code check that the reference values appear in the rows of the last query AskDB ran. This is not part of the pass fail criteria, but it helps debug wrong answers.

Cases cover lookups, aggregation, joins, dates, ties in rankings, ambiguous questions, large results, questions the data can't answer, and requests to change data, which must be refused.

The eval calls the real Claude API and costs about **$1 per run**, so it's excluded from `./mvnw test`. With Pagila running (`docker compose up`) and `ANTHROPIC_API_KEY` in `.env`:

```sh
mvn test -Peval                                        # all cases
mvn test -Peval -Deval.ids=count-films,store-most-revenue   # selected cases
mvn test -Peval -Deval.variant=v1                      # a new variant, after changing a prompt or model
```

Results go to `.claude/hillclimb/ask-pagila/<variant>/results.jsonl`, one JSON row per case. A rerun skips cases already in the file, so delete the variant's folder to grade from scratch (for example, after changing the judge). The eval runs in UTC, because Postgres sessions use the JVM's timezone and that decides what "March 2022" means.
