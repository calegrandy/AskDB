package io.github.calegrandy.askdb.eval;

import io.github.calegrandy.askdb.ContainersConfig;
import io.github.calegrandy.askdb.dto.QueryResponse;
import io.github.calegrandy.askdb.enums.ConnectionType;
import io.github.calegrandy.askdb.exception.QueryFailedException;
import io.github.calegrandy.askdb.model.Connection;
import io.github.calegrandy.askdb.model.QueryAttempt;
import io.github.calegrandy.askdb.model.QueryHistory;
import io.github.calegrandy.askdb.query.DatabaseQueryService;
import io.github.calegrandy.askdb.query.SqlTool;
import io.github.calegrandy.askdb.repository.ConnectionRepository;
import io.github.calegrandy.askdb.repository.QueryHistoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Runs the AskDB eval: every case in cases.yaml through the real question flow (real Claude, real Pagila),
 * graded by {@link AnswerJudge} (headline) and {@link ResultMatcher} (diagnostic).
 *
 * <p>Calls the paid Claude API, so it's excluded from normal builds. Run it with:
 * <pre>mvn test -Peval [-Deval.ids=id1,id2] [-Deval.reps=1] [-Deval.variant=baseline]</pre>
 * Requires the Pagila container (docker compose up -d pagila) and ANTHROPIC_API_KEY in .env.
 *
 * <p>Writes to .claude/hillclimb/ask-pagila/&lt;variant&gt;/: results.jsonl (one graded row per case and rep),
 * traces/ (each case's full exchange) and errors.jsonl (attempts that failed before they could be graded,
 * kept apart so plumbing failures never count as wrong answers). Re-running skips cases already in
 * results.jsonl, so an interrupted run resumes where it stopped.
 */
@Tag("eval")
@SpringBootTest
@Import(ContainersConfig.class)
class AskDbEval {

    private static final Path CASES = Path.of("src/test/resources/evals/pagila/cases.yaml");
    private static final Path FLOW_DIR = Path.of(".claude/hillclimb/ask-pagila");

    // Tie and ambiguity cases have several valid query shapes, so comparing data is only meaningful elsewhere.
    private static final Set<String> NO_RESULT_MATCH_CATEGORIES = Set.of("ranking-ties", "ambiguity");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    DatabaseQueryService queryService;

    @Autowired
    QueryHistoryRepository queryHistoryRepository;

    @Autowired
    ConnectionRepository connectionRepository;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Value("${spring.ai.anthropic.chat.options.model}")
    String model;

    /** What one (case, rep) produced: a graded row and its trace, or an error. */
    private record Outcome(Map<String, Object> row, List<Map<String, Object>> trace, Map<String, Object> error) {
    }

    private static class HarnessException extends RuntimeException {
        final String failureClass;

        HarnessException(String failureClass, String message, Throwable cause) {
            super(message, cause);
            this.failureClass = failureClass;
        }
    }

    @Test
    void run() throws Exception {
        String variant = System.getProperty("eval.variant", "baseline");
        int reps = Integer.getInteger("eval.reps", 1);
        int concurrency = Integer.getInteger("eval.concurrency", 4);
        Duration caseTimeout = Duration.ofSeconds(Integer.getInteger("eval.timeoutSeconds", 300));
        Set<String> onlyIds = parseIds(System.getProperty("eval.ids"));

        List<EvalCase> cases = EvalCase.load(CASES).stream()
                .filter(c -> onlyIds.isEmpty() || onlyIds.contains(c.id()))
                .toList();
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("No cases match eval.ids=" + onlyIds);
        }

        String pagilaUrl = System.getProperty("eval.pagilaUrl", "jdbc:postgresql://localhost:5433/pagila");
        JdbcTemplate pagila = new JdbcTemplate(new DriverManagerDataSource(pagilaUrl, "askdb_reader", "askdb_reader"));
        try {
            pagila.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            throw new IllegalStateException("Can't reach Pagila at " + pagilaUrl + ". Start it: docker compose up -d pagila", e);
        }
        Connection connection = connectionRepository.save(Connection.builder()
                .username("askdb_reader").password("askdb_reader").url(pagilaUrl).type(ConnectionType.POSTGRESQL)
                .build());

        Path variantDir = FLOW_DIR.resolve(variant);
        Files.createDirectories(variantDir.resolve("traces"));
        Path results = variantDir.resolve("results.jsonl");
        Path errors = variantDir.resolve("errors.jsonl");
        Set<String> done = completedKeys(results);

        AnswerJudge judge = new AnswerJudge(chatClientBuilder);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        Semaphore slots = new Semaphore(concurrency);
        List<CompletableFuture<Void>> running = new ArrayList<>();
        long start = System.nanoTime();

        for (EvalCase evalCase : cases) {
            for (int rep = 0; rep < reps; rep++) {
                if (done.contains(key(evalCase.id(), rep))) {
                    continue;
                }
                int r = rep;
                slots.acquire();  // start each case as it's submitted, so the timeout below measures its own run
                running.add(CompletableFuture
                        .supplyAsync(() -> runCase(evalCase, r, connection.getId(), pagila, judge), executor)
                        .orTimeout(caseTimeout.toSeconds(), TimeUnit.SECONDS)
                        .handle((outcome, failure) -> {
                            slots.release();
                            if (failure != null) {
                                outcome = timeoutOrCrash(evalCase, r, failure, caseTimeout);
                            }
                            record(outcome, variantDir, results, errors);
                            return null;
                        }));
            }
        }
        CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).join();
        executor.shutdownNow();

        printSummary(results, errors, cases, Duration.ofNanos(System.nanoTime() - start));
    }

    private Outcome runCase(EvalCase evalCase, int rep, Long connectionId, JdbcTemplate pagila, AnswerJudge judge) {
        try {
            List<Map<String, Object>> referenceRows = referenceRows(evalCase, pagila);

            QueryResponse response;
            long askStart = System.nanoTime();
            try {
                response = queryService.ask(connectionId, evalCase.question());
            } catch (QueryFailedException e) {
                throw new HarnessException("serving_error", e.getMessage(), e);
            }
            double latencySeconds = Duration.ofNanos(System.nanoTime() - askStart).toMillis() / 1000.0;

            QueryHistory history = queryHistoryRepository.findById(response.queryId()).orElseThrow();
            if (history.getModel() == null || !history.getModel().startsWith(model)) {
                throw new HarnessException("model_mismatch",
                        "Asked for " + model + " but was served by " + history.getModel(), null);
            }

            AnswerJudge.Judgement judgement;
            try {
                judgement = judge.judge(evalCase, referenceRows, response.answer());
            } catch (RuntimeException e) {
                throw new HarnessException("grader_error", e.getMessage(), e);
            }

            Map<String, Object> grade = new LinkedHashMap<>();
            grade.put("answer_correct", judgement.verdict().pass() ? 1 : 0);
            if (evalCase.expect() == EvalCase.Expect.ANSWER && !NO_RESULT_MATCH_CATEGORIES.contains(evalCase.category())) {
                grade.put("sql_result_match", ResultMatcher.matches(referenceRows, response.rows()) ? 1 : 0);
            }

            List<QueryAttempt> attempts = history.getAttempts();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prompt_id", evalCase.id());
            row.put("rep", rep);
            row.put("prompt", evalCase.question());
            row.put("tags", evalCase.tags());
            row.put("status", "ok");
            row.put("grade", grade);
            row.put("explanation", Map.of("answer_correct", judgement.verdict().reason()));
            row.put("model", history.getModel());
            row.put("usage", Map.of("input_tokens", history.getInputTokens(), "output_tokens", history.getOutputTokens()));
            row.put("latency_s", latencySeconds);
            row.put("tool_calls", attempts.size());
            row.put("judge_model", judgement.model());
            row.put("judge_usage", usage(judgement.usage()));
            row.put("meta", Map.of(
                    "expect", evalCase.expect().name().toLowerCase(),
                    "query_id", response.queryId(),
                    "failed_attempts", attempts.stream().filter(a -> a.error() != null).count()));

            return new Outcome(row, trace(evalCase, attempts, response.answer()), null);
        } catch (HarnessException e) {
            return new Outcome(null, null, error(evalCase, rep, e.failureClass, e.getMessage()));
        } catch (RuntimeException e) {
            return new Outcome(null, null, error(evalCase, rep, "harness_error", e.toString()));
        }
    }

    // Runs the reference query read-only, through the same tool AskDB uses, so values are formatted identically.
    private static List<Map<String, Object>> referenceRows(EvalCase evalCase, JdbcTemplate pagila) {
        if (evalCase.referenceSql() == null) {
            return null;
        }
        Object result = new SqlTool(pagila).runSql(evalCase.referenceSql());
        if (result instanceof Map<?, ?> error) {
            throw new HarnessException("harness_error", "Reference SQL failed: " + error.get("error"), null);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result;
        return rows;
    }

    private static List<Map<String, Object>> trace(EvalCase evalCase, List<QueryAttempt> attempts, String answer) {
        List<Map<String, Object>> turns = new ArrayList<>();
        turns.add(Map.of("role", "user", "content", evalCase.question()));
        for (QueryAttempt attempt : attempts) {
            turns.add(Map.of("role", "tool_call", "name", "run_sql",
                    "content", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("sql", attempt.sql()))));
            turns.add(Map.of("role", "tool_result", "name", "run_sql", "content", attempt.error() != null
                    ? "Error: " + attempt.error()
                    : attempt.rowCount() + " rows in " + attempt.durationMs() + " ms"));
        }
        turns.add(Map.of("role", "assistant", "content", answer == null ? "" : answer));
        return turns;
    }

    private static Outcome timeoutOrCrash(EvalCase evalCase, int rep, Throwable failure, Duration caseTimeout) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        return cause instanceof TimeoutException
                ? new Outcome(null, null, error(evalCase, rep, "timeout", "No result within " + caseTimeout.toSeconds() + "s"))
                : new Outcome(null, null, error(evalCase, rep, "harness_error", String.valueOf(cause)));
    }

    private static Map<String, Object> error(EvalCase evalCase, int rep, String failureClass, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("prompt_id", evalCase.id());
        error.put("rep", rep);
        error.put("failure_class", failureClass);
        error.put("message", message);
        return error;
    }

    private static synchronized void record(Outcome outcome, Path variantDir, Path results, Path errors) {
        try {
            if (outcome.error() != null) {
                append(errors, outcome.error());
                return;
            }
            String id = (String) outcome.row().get("prompt_id");
            int rep = (int) outcome.row().get("rep");
            Files.writeString(variantDir.resolve("traces").resolve(id + "_rep" + rep + ".json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(outcome.trace()));
            append(results, outcome.row());  // written as each case finishes, so a crash loses nothing done
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void append(Path file, Map<String, Object> line) throws IOException {
        Files.writeString(file, JSON.writeValueAsString(line) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static Set<String> completedKeys(Path results) throws IOException {
        if (!Files.exists(results)) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(results)) {
            if (!line.isBlank()) {
                Map<?, ?> row = JSON.readValue(line, Map.class);
                keys.add(key((String) row.get("prompt_id"), ((Number) row.get("rep")).intValue()));
            }
        }
        return keys;
    }

    private static String key(String id, int rep) {
        return id + "#" + rep;
    }

    private static Map<String, Object> usage(Usage usage) {
        return Map.of("input_tokens", usage.getPromptTokens(), "output_tokens", usage.getCompletionTokens());
    }

    private static Set<String> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.stream(ids.split(",")).map(String::trim).toList());
    }

    // Headline numbers, recomputed from every row on disk (this run and any earlier ones it resumed).
    @SuppressWarnings("unchecked")
    private static void printSummary(Path results, Path errors, List<EvalCase> cases, Duration elapsed) throws IOException {
        Set<String> ids = new HashSet<>(cases.stream().map(EvalCase::id).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Files.exists(results)) {
            for (String line : Files.readAllLines(results)) {
                Map<String, Object> row = JSON.readValue(line, Map.class);
                if (ids.contains((String) row.get("prompt_id"))) {
                    rows.add(row);
                }
            }
        }
        long errorCount = Files.exists(errors) ? Files.readAllLines(errors).stream().filter(l -> !l.isBlank()).count() : 0;

        int n = rows.size();
        long correct = rows.stream().filter(r -> ((Map<String, Number>) r.get("grade")).get("answer_correct").intValue() == 1).count();
        List<Integer> matches = rows.stream()
                .map(r -> ((Map<String, Number>) r.get("grade")).get("sql_result_match"))
                .filter(Objects::nonNull).map(Number::intValue).toList();
        double p = n == 0 ? 0 : (double) correct / n;
        double halfWidth = n == 0 ? 0 : 1.96 * Math.sqrt(p * (1 - p) / n);

        System.out.printf("%n===== AskDB eval: %d graded, %d errors (see errors.jsonl), %ds =====%n",
                n, errorCount, elapsed.toSeconds());
        System.out.printf("answer_correct:   %.0f%% (%d/%d), 95%% CI +/- %.0f points%n", p * 100, correct, n, halfWidth * 100);
        System.out.printf("sql_result_match: %d/%d applicable cases%n", matches.stream().mapToInt(Integer::intValue).sum(), matches.size());
        System.out.printf("Results: %s%n%n", results.toAbsolutePath());
    }
}
