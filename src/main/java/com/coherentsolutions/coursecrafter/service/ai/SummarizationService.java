package com.coherentsolutions.coursecrafter.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Wraps a Chat / Completion call that turns raw uploads into
 * slide-friendly, “clean” Markdown.
 */
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final ChatClient chatClient;   // injected from AiConfig

    /**
     * Normalises & summarises an arbitrary Markdown fragment.
     *
     * @param markdown raw text from an instructor upload
     * @return condensed, well-formed Markdown
     */
    public String summarize(String markdown) {

        return chatClient.prompt()
                .system("""
                    You are CourseCrafter AI. Convert the incoming text \
                    into concise Markdown slides:
                      • keep headings / lists / code blocks
                      • remove greetings, applause, “thanks”, etc.
                      • aim for ≤ 15 tokens per bullet
                    Return *only* Markdown.
                    """)
                .user(markdown)
                .call()
                .content();
    }
}