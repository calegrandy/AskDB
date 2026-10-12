package io.github.calegrandy.askdb;

import org.springframework.ai.anthropic.AnthropicChatOptions;
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
    private ChatOptions lastOptions;

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

    /** The options AskDB sent with the most recent call. */
    public ChatOptions lastOptions() {
        return lastOptions;
    }

    public void reset() {
        sqlToRun.clear();
        answer = "Fake answer";
        failure = null;
        lastOptions = null;
    }

    @Override
    public ChatOptions getOptions() {
        // Anthropic options, like the real model: ChatClient merges each request's options (including the tools
        // registered with .tools(...)) into this type, so it must be able to hold them.
        return AnthropicChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastOptions = prompt.getOptions();
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

        // Like the real Claude Opus 5.5: an (empty, by default) thinking part comes before the answer.
        AssistantMessage thinking = AssistantMessage.builder()
                .content("")
                .properties(Map.of("signature", "fake-signature"))
                .build();

        return new ChatResponse(
                List.of(new Generation(thinking), new Generation(new AssistantMessage(answer))),
                ChatResponseMetadata.builder()
                        .model(MODEL)
                        .usage(new DefaultUsage(INPUT_TOKENS, OUTPUT_TOKENS))
                        .build());
    }
}
