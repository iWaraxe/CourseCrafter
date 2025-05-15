package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Wraps a Chat / Completion call that turns raw uploads into
 * slide-friendly, “clean” Markdown.
 */
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
        // Get course structure from ContentHierarchyService
        String courseStructure = contentHierarchyService.generateDetailedOutlineContext(courseName);

        return chatClient.prompt()
                .system("""
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
                """.formatted(courseName, audience, reportDate, courseStructure))
                .user(markdown)
                .call()
                .content();
    }

    /**
     * Basic summarization for backward compatibility
     */
    public String summarize(String markdown) {
        return chatClient.prompt()
                .system("""
                    You are CourseCrafter AI. Convert the incoming text \
                    into concise Markdown slides:
                      • keep headings / lists / code blocks
                      • remove greetings, applause, "thanks", etc.
                      • aim for ≤ 15 tokens per bullet
                    Return *only* Markdown.
                    """)
                .user(markdown)
                .call()
                .content();
    }
}