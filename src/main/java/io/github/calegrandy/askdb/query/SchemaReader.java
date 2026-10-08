package io.github.calegrandy.askdb.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes a PostgreSQL database's tables and columns as compact text for the model, one table per line:
 * {@code public.film(film_id integer, title text, ...)}
 */
public final class SchemaReader {

    // Skips system schemas, and partitions of partitioned tables (their columns repeat the parent's).
    private static final String COLUMNS_SQL = """
            SELECT c.table_schema, c.table_name, c.column_name, c.data_type
            FROM information_schema.columns c
            WHERE c.table_schema NOT IN ('pg_catalog', 'information_schema')
              AND NOT EXISTS (
                  SELECT 1 FROM pg_catalog.pg_class pc
                  JOIN pg_catalog.pg_namespace pn ON pn.oid = pc.relnamespace
                  WHERE pn.nspname = c.table_schema AND pc.relname = c.table_name AND pc.relispartition)
            ORDER BY c.table_schema, c.table_name, c.ordinal_position
            """;

    private SchemaReader() {
    }

    public static String describe(JdbcTemplate jdbcTemplate) {
        Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
        jdbcTemplate.query(COLUMNS_SQL, rs -> {
            String table = rs.getString("table_schema") + "." + rs.getString("table_name");
            String column = rs.getString("column_name") + " " + rs.getString("data_type");
            columnsByTable.computeIfAbsent(table, t -> new ArrayList<>()).add(column);
        });

        return columnsByTable.entrySet().stream()
                .map(e -> e.getKey() + "(" + String.join(", ", e.getValue()) + ")")
                .collect(Collectors.joining("\n"));
    }
}
