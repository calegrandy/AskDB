package io.github.calegrandy.askdb.query;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the parts of answering a question that don't involve the model, against a real Postgres
 * playing the role of a user's registered database. No Spring context needed.
 */
class QueryToolsTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startDatabase() {
        postgres.start();
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbcTemplate.execute("""
                CREATE TABLE film (film_id integer PRIMARY KEY, title text, release_date date);
                INSERT INTO film VALUES (1, 'Alien Center', '2022-05-01'), (2, 'Brooklyn Desert', '2022-06-15');
                CREATE TABLE sales (amount numeric) PARTITION BY RANGE (amount);
                CREATE TABLE sales_low PARTITION OF sales FOR VALUES FROM (0) TO (100);
                """);
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @Test
    void schemaListsTablesWithColumnsAndSkipsPartitions() {
        String schema = SchemaReader.describe(jdbcTemplate);

        assertThat(schema).contains("public.film(film_id integer, title text, release_date date)");
        assertThat(schema).contains("public.sales(amount numeric)");
        assertThat(schema).doesNotContain("sales_low");
    }

    @Test
    void runSqlReturnsRowsWithReadableValuesAndRemembersQuery() {
        SqlTool tool = new SqlTool(jdbcTemplate);

        Object result = tool.runSql("SELECT title, release_date FROM film ORDER BY film_id");

        assertThat(result).isEqualTo(List.of(
                Map.of("title", "Alien Center", "release_date", "2022-05-01"),
                Map.of("title", "Brooklyn Desert", "release_date", "2022-06-15")));
        assertThat(tool.lastSql()).isEqualTo("SELECT title, release_date FROM film ORDER BY film_id");
        assertThat(tool.lastRows()).hasSize(2);
    }

    @Test
    void runSqlReturnsErrorForBadQueryAndKeepsLastSuccessfulQuery() {
        SqlTool tool = new SqlTool(jdbcTemplate);
        tool.runSql("SELECT title FROM film");

        Object result = tool.runSql("SELECT no_such_column FROM film");

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error")).asString().contains("no_such_column");
        assertThat(tool.lastSql()).isEqualTo("SELECT title FROM film");
    }

    @Test
    void runSqlCapsRows() {
        SqlTool tool = new SqlTool(jdbcTemplate);

        Object result = tool.runSql("SELECT generate_series(1, 500) AS n");

        assertThat((List<?>) result).hasSize(SqlTool.MAX_ROWS);
    }
}
