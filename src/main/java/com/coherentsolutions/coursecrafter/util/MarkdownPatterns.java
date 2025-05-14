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
    public static final Pattern SLIDE_PATTERN = Pattern.compile("^##### \\[seq:(\\d+)\\] ([^\\n]+)([\\s\\S]*?)(?=^---$|^##### |$)",
            Pattern.MULTILINE);

    // Component patterns
    public static final Pattern COMPONENT_PATTERN = Pattern.compile(
            "^\\s*#{6}\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*[:]?\\s*$(.*?)(?=^\\s*#{6}|^\\s*-{3,}|$)",
            Pattern.DOTALL | Pattern.MULTILINE);

    // Table structure pattern
    public static final Pattern TABLE_PATTERN = Pattern.compile("\\|(.+?)\\|", Pattern.DOTALL);

    // Private constructor to prevent instantiation
    private MarkdownPatterns() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}