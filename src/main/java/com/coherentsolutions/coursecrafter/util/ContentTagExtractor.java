package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.tag.model.Tag;
import com.coherentsolutions.coursecrafter.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to extract tags from course content and create tag relationships.
 * This improves searchability and content categorization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(4) // Run after slide component extraction
public class ContentTagExtractor implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final TagRepository tagRepository;

    // Keywords to look for in content and map to relevant tags
    private static final Map<String, String> KEYWORD_TO_TAG = Map.ofEntries(
            Map.entry("ChatGPT", "GPT-4"),
            Map.entry("GPT-4", "GPT-4"),
            Map.entry("Claude", "Claude"),
            Map.entry("Gemini", "Gemini"),
            Map.entry("Perplexity", "Perplexity"),
            Map.entry("Mistral", "Mistral"),
            Map.entry("prompt", "Prompt Engineering"),
            Map.entry("ollama", "LocalLLM"),
            Map.entry("API", "API Integration"),
            Map.entry("image", "Visual AI"),
            Map.entry("visual", "Visual AI"),
            Map.entry("reasoning", "Reasoning"),
            Map.entry("code", "Coding"),
            Map.entry("programming", "Coding"),
            Map.entry("token", "Tokenization"),
            Map.entry("hallucination", "Hallucinations"),
            Map.entry("bias", "Bias"),
            Map.entry("AI language model", "LLM"),
            Map.entry("LLM", "LLM"),
            Map.entry("generative AI", "Generative AI"),
            Map.entry("multi-agent", "Multi-agent AI"),
            Map.entry("ethical", "Ethics"),
            Map.entry("ethics", "Ethics"),
            Map.entry("system prompt", "System Prompts"),
            Map.entry("temperature", "Temperature"),
            Map.entry("workflow", "Workflow Integration"),
            Map.entry("automation", "Automation")
    );

    // Categories for organizing tags
    private static final Map<String, String> TAG_CATEGORIES = Map.ofEntries(
            Map.entry("GPT-4", "MODEL"),
            Map.entry("Claude", "MODEL"),
            Map.entry("Gemini", "MODEL"),
            Map.entry("Perplexity", "TOOL"),
            Map.entry("Mistral", "MODEL"),
            Map.entry("LocalLLM", "TECHNOLOGY"),
            Map.entry("Prompt Engineering", "SKILL"),
            Map.entry("API Integration", "TECHNOLOGY"),
            Map.entry("Visual AI", "CAPABILITY"),
            Map.entry("Reasoning", "CAPABILITY"),
            Map.entry("Coding", "CAPABILITY"),
            Map.entry("Tokenization", "CONCEPT"),
            Map.entry("Hallucinations", "CONCEPT"),
            Map.entry("Bias", "CONCEPT"),
            Map.entry("LLM", "CONCEPT"),
            Map.entry("Generative AI", "TECHNOLOGY"),
            Map.entry("Multi-agent AI", "TECHNOLOGY"),
            Map.entry("Ethics", "CONCEPT"),
            Map.entry("System Prompts", "TECHNIQUE"),
            Map.entry("Temperature", "CONCEPT"),
            Map.entry("Workflow Integration", "SKILL"),
            Map.entry("Automation", "CAPABILITY")
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Skip if tags already exist
        if (tagRepository.count() > 0) {
            log.info("Tags already exist. Skipping tag extraction.");
            return;
        }

        log.info("Starting to extract tags from course content...");
        extractTags();
        log.info("Tag extraction completed.");
    }

    @Transactional
    public void extractTags() {
        // Pre-create all tags
        Map<String, Tag> tagMap = createTags();

        // Get all content nodes
        List<ContentNode> contentNodes = contentNodeRepository.findAll();
        log.info("Found {} content nodes to analyze for tags", contentNodes.size());

        // Process each node
        for (ContentNode node : contentNodes) {
            processNodeForTags(node, tagMap);
        }
    }

    private Map<String, Tag> createTags() {
        Map<String, Tag> tagMap = new HashMap<>();

        // Create each tag based on the predefined list
        for (Map.Entry<String, String> entry : TAG_CATEGORIES.entrySet()) {
            String tagName = entry.getKey();
            String category = entry.getValue();

            Tag tag = Tag.builder()
                    .name(tagName)
                    .category(category)
                    .createdAt(LocalDateTime.now())
                    .build();

            tag = tagRepository.save(tag);
            tagMap.put(tagName, tag);
            log.info("Created tag: {} ({})", tagName, category);
        }

        return tagMap;
    }

    private void processNodeForTags(ContentNode node, Map<String, Tag> tagMap) {
        // Get content from latest version
        String nodeContent = "";

        // Also include title and description
        String allContent = node.getTitle() + " " +
                (node.getDescription() != null ? node.getDescription() : "") + " " +
                nodeContent;

        // Find matching keywords
        Set<String> matchedTags = new HashSet<>();

        for (Map.Entry<String, String> entry : KEYWORD_TO_TAG.entrySet()) {
            String keyword = entry.getKey();
            String tagName = entry.getValue();

            // Use word boundary regex to match whole words
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(allContent);

            if (matcher.find()) {
                matchedTags.add(tagName);
            }
        }

        // Skip if no tags found
        if (matchedTags.isEmpty()) {
            return;
        }

        // Add tags to the node
        Set<Tag> nodeTags = node.getTags();
        if (nodeTags == null) {
            nodeTags = new HashSet<>();
            node.setTags(nodeTags);
        }

        for (String tagName : matchedTags) {
            Tag tag = tagMap.get(tagName);
            if (tag != null) {
                nodeTags.add(tag);
                log.debug("Added tag '{}' to node: {}", tagName, node.getTitle());
            }
        }

        // Save the updated node
        contentNodeRepository.save(node);
        log.info("Added {} tags to node: {}", matchedTags.size(), node.getTitle());
    }
}