package com.autobook.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Test double for Gemini. It never calls Google. A test gives it a {@link Script} that decides what
 * the "model" does with each prompt, including calling the real tool callbacks that Spring AI's
 * {@code ChatClient} attached to the prompt, exactly as a model's function call would.
 *
 * <p>These tests prove AutoBook's wiring, security, and transactions around the model. They do not
 * prove anything about the quality of live Gemini answers.
 */
public class ScriptedChatModel implements ChatModel {

    @FunctionalInterface
    public interface Script {
        String respond(Prompt prompt, ToolInvoker tools);
    }

    /** Calls the tools that were attached to the current prompt, by name, with JSON arguments. */
    public static final class ToolInvoker {

        private final Map<String, ToolCallback> callbacks;
        private final List<String> calls;

        ToolInvoker(Map<String, ToolCallback> callbacks, List<String> calls) {
            this.callbacks = callbacks;
            this.calls = calls;
        }

        public String call(String name, String jsonArguments) {
            ToolCallback callback = callbacks.get(name);
            if (callback == null) {
                throw new AssertionError("Tool " + name + " was not offered to the model; offered: " + callbacks.keySet());
            }
            calls.add(name);
            return callback.call(jsonArguments);
        }

        public List<String> names() {
            return new ArrayList<>(callbacks.keySet());
        }

        /** The JSON schema of a tool's arguments, as the model would see it. */
        public String schema(String name) {
            return callbacks.get(name).getToolDefinition().inputSchema();
        }
    }

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final List<String> toolCalls = new CopyOnWriteArrayList<>();
    private volatile Script script = (prompt, tools) -> "OK";
    private volatile RuntimeException failure;

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        RuntimeException currentFailure = failure;
        if (currentFailure != null) {
            throw currentFailure;
        }
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null) {
            for (ToolCallback callback : options.getToolCallbacks()) {
                callbacks.put(callback.getToolDefinition().name(), callback);
            }
        }
        String text = script.respond(prompt, new ToolInvoker(callbacks, toolCalls));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Override
    public ChatOptions getOptions() {
        // Tool-calling options, so ChatClient attaches the tool callbacks to every prompt.
        return ToolCallingChatOptions.builder().build();
    }

    public void script(Script newScript) {
        this.script = newScript;
    }

    public void failWith(RuntimeException error) {
        this.failure = error;
    }

    public void reset() {
        prompts.clear();
        toolCalls.clear();
        failure = null;
        script = (prompt, tools) -> "OK";
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public List<String> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public Prompt lastPrompt() {
        if (prompts.isEmpty()) {
            throw new AssertionError("The chat model was never called.");
        }
        return prompts.get(prompts.size() - 1);
    }

    /** All message text of a prompt (system, history, and user), for assertions. */
    public static String text(Prompt prompt) {
        return prompt.getInstructions().stream()
                .map(Message::getText)
                .filter(text -> text != null)
                .collect(Collectors.joining("\n"));
    }
}
