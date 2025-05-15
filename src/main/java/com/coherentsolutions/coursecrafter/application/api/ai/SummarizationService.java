package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Wraps a Chat / Completion call that turns raw uploads into
 * slide-friendly, “clean” Markdown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final ChatClient chatClient;   // injected from AiConfig
    private final ContentHierarchyService contentHierarchyService;

    /**
     * Normalises & summarises an arbitrary Markdown fragment.
     *
     * @param markdown raw text from an instructor upload
     * @return condensed, well-formed Markdown
     */
    public String summarize(String markdown, String courseName, String audience, LocalDate reportDate) {

        log.debug("Summarizing content for course: {}, audience: {}, reportDate: {}",
                courseName, audience, reportDate);
        log.debug("Input content length: {} characters", markdown.length());

        // Get course structure from ContentHierarchyService
        String courseStructure = contentHierarchyService.generateDetailedOutlineContext(courseName);
        log.debug("Course structure length: {} characters", courseStructure.length());

        String systemPrompt = String.format("""
            You are CourseCrafter AI, an expert educational content specialist.
            
            You're analyzing new content to be integrated into an existing course named "%s".
            This course is designed for %s and serves as an introduction to AI concepts.
            
            The content you're analyzing is from a report dated %s.
            
            COURSE STRUCTURE:
            %s
            
            Your task:
            1. Analyze this new content against the existing course structure
            2. Identify specific improvements or additions for:
               • Script content (explanations, concepts, definitions)
               • Visual elements (diagrams, charts, examples to show)
               • Notes (interesting facts, resources, references)
               • Demonstrations (new prompts or techniques to showcase)
            
            Format your response as structured, clean Markdown that:
            • Maintains all important headings and code blocks
            • Removes unnecessary elements like greetings
            • Uses concise language
            • Clearly indicates where each suggestion belongs in the course
            
            Return ONLY well-formatted Markdown with actionable suggestions.
            """, courseName, audience, reportDate, courseStructure);

        log.debug("System prompt: {}", systemPrompt);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(markdown)
                .call();

        String content = response.content();
        log.debug("AI summarization response: {}", content);
        return content;
    }

    /**
     * Basic summarization for backward compatibility
     */
    public String summarize(String markdown) {
        log.debug("Simple summarize method called with content length: {} characters",
                markdown.length());

        String systemPrompt = """
            You are CourseCrafter AI. Convert the incoming text \
            into concise Markdown slides:
              • keep headings / lists / code blocks
              • remove greetings, applause, "thanks", etc.
              • aim for ≤ 15 tokens per bullet
            Return *only* Markdown.
            """;

        log.debug("System prompt: {}", systemPrompt);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(markdown)
                .call();

        String content = response.content();
        log.debug("AI summarization response: {}", content);
        return content;
    }
}