package io.github.calegrandy.askdb.eval;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** One eval question, as written in cases.yaml. {@code referenceSql} and {@code rubric} are explained there. */
public record EvalCase(String id, String question, List<String> tags, Expect expect, String referenceSql, String rubric) {

    public enum Expect { ANSWER, CANNOT_ANSWER, REFUSE }

    public String category() {
        return tags.getFirst();
    }

    @SuppressWarnings("unchecked")
    public static List<EvalCase> load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
            return cases.stream()
                    .map(c -> new EvalCase(
                            (String) c.get("id"),
                            (String) c.get("question"),
                            (List<String>) c.get("tags"),
                            Expect.valueOf(((String) c.get("expect")).toUpperCase()),
                            (String) c.get("reference_sql"),
                            (String) c.get("rubric")))
                    .toList();
        }
    }
}
