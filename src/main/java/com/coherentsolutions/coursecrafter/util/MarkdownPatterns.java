package com.coherentsolutions.coursecrafter.util;

import java.util.regex.Pattern;

/**
 * Centralized repository for regex patterns used across the application.
 * This ensures consistency in pattern matching across different components.
 */
public final class MarkdownPatterns {

    // Document structure patterns
    public static final Pattern COURSE_PATTERN = Pattern.compile("^# ([^\\n]+)", Pattern.MULTILINE);
    public static final Pattern LECTURE_PATTERN = Pattern.compile("^## ([^\\n]+)", Pattern.MULTILINE);
    public static final Pattern SECTION_PATTERN = Pattern.compile("^### ([^\\n]+)", Pattern.MULTILINE);
    public static final Pattern TOPIC_PATTERN = Pattern.compile("^#### ([^\\n]+)", Pattern.MULTILINE);
    /**
     * This pattern is specifically designed to capture slide headers and their content
     * Group 1: Sequence number
     * Group 2: Slide title
     * Group 3: Complete slide content (should include all components)
     */
    public static final Pattern SLIDE_PATTERN = Pattern.compile(
            "##### \\[seq:(\\d+)\\] ([^\\n]+)\\n((?:[\\s\\S](?!##### \\[seq:[0-9]+\\]))*)(?=##### |$)",
            Pattern.MULTILINE
    );
    /**
     * This pattern matches individual slide components with their content
     * It's designed to capture each component type and its content separately
     */
    public static final Pattern COMPONENT_PATTERN = Pattern.compile(
            "###### (SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*\\n([\\s\\S]*?)(?=\\s*###### |$)",
            Pattern.DOTALL
    );

    // Table structure pattern
    public static final Pattern TABLE_PATTERN = Pattern.compile("\\|(.+?)\\|", Pattern.DOTALL);

    // Private constructor to prevent instantiation
    private MarkdownPatterns() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}