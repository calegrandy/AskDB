package io.github.calegrandy.askdb;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A scripted stand-in for Claude, so tests can run the whole question flow for free and get the same result
 * every time. It calls the run_sql tool with the queries it's given, then returns a fixed answer and token usage.
 */
public class FakeChatModel implements ChatModel {

    public static final String MODEL = "fake-model";
    public static final int INPUT_TOKENS = 1200;
    public static final int OUTPUT_TOKENS = 80;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<String> sqlToRun = new ArrayList<>();
    private String answer = "Fake answer";
    private RuntimeException failure;

    public FakeChatModel willRunSql(String... sql) {
        sqlToRun.addAll(List.of(sql));
        return this;
    }

    public FakeChatModel willAnswer(String answer) {
        this.answer = answer;
        return this;
    }

    public FakeChatModel willFail(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public void reset() {
        sqlToRun.clear();
        answer = "Fake answer";
        failure = null;
    }

    @Override
    public ChatOptions getOptions() {
        // Tool-calling options, so ChatClient passes the tools registered with .tools(...) through to call().
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (failure != null) {
            throw failure;
        }
        ToolCallback runSql = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks().stream()
                .filter(tool -> tool.getToolDefinition().name().equals("run_sql"))
                .findFirst()
                .orElseThrow();
        for (String sql : sqlToRun) {
            runSql.call(JSON.writeValueAsString(Map.of("sql", sql)));
        }

        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(answer))),
                ChatResponseMetadata.builder()
                        .model(MODEL)
                        .usage(new DefaultUsage(INPUT_TOKENS, OUTPUT_TOKENS))
                        .build());
    }
}
