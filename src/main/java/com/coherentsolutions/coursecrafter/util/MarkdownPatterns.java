package com.coherentsolutions.coursecrafter.util;

import java.util.regex.Pattern;

/**
 * Centralized repository for regex patterns used across the application.
 * This ensures consistency in pattern matching across different components.
 */
public final class MarkdownPatterns {

    // Document structure patterns
    // These patterns are primarily for identifying the titles of these elements.
    // The parser logic is responsible for slicing the content blocks.
    public static final Pattern COURSE_PATTERN = Pattern.compile("^#\\s+([^\\n]+?)\\s*$", Pattern.MULTILINE);
    public static final Pattern LECTURE_PATTERN = Pattern.compile("^##\\s+([^\\n]+?)\\s*$", Pattern.MULTILINE);
    public static final Pattern SECTION_PATTERN = Pattern.compile("^###\\s+([^\\n]+?)\\s*$", Pattern.MULTILINE);
    public static final Pattern TOPIC_PATTERN = Pattern.compile("^####\\s+([^\\n]+?)\\s*$", Pattern.MULTILINE);

    /**
     * SLIDE_PATTERN:
     * Group 1: Sequence number (e.g., "010")
     * Group 2: Slide title (everything on the H5 line after [seq:xxx])
     * Group 3: Slide body content (everything AFTER the H5 line, until a terminator)
     * Terminators: Next H5, H4, H3, H2, H1, '---' separator, or end of current parsing block.
     */
    public static final Pattern SLIDE_PATTERN = Pattern.compile(
            // Start of line, H5, space, [seq:digits], space, title
            "^#####\\s+\\[seq:(\\d+)\\]\\s+([^\\n]+?)\\s*$" + // Group 1 (seq), Group 2 (title). Ensures title is the rest of the H5 line.
                    // Content block (Group 3): Optional, starts on a new line.
                    "(?:\\r?\\n(.*?))?" + // Non-capturing group for the newline and content. Group 3 is (.*?).
                    // Positive lookahead for terminators.
                    "(?=\\Z|^#####\\s+\\[seq:\\d+\\]|^\\s*---\\s*$|^#{1,4}\\s)",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    /**
     * COMPONENT_PATTERN:
     * Group 1: Component type (SCRIPT, VISUAL, NOTES, DEMONSTRATION)
     * Group 2: Component content (everything AFTER the H6 line, until next H6 or end of slide body)
     * This pattern operates on the slide body content (Group 3 from SLIDE_PATTERN).
     */
    public static final Pattern COMPONENT_PATTERN = Pattern.compile(
            // Start of line (within slide body), H6, space, type
            "^######\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*$" + // Group 1 (type). Ensures type is the only thing on H6 line.
                    // Content block (Group 2): Optional, starts on a new line.
                    "(?:\\r?\\n(.*?))?" + // Non-capturing group for newline and content. Group 2 is (.*?).
                    // Positive lookahead for terminators.
                    "(?=\\Z|^######\\s+(?:SCRIPT|VISUAL|NOTES|DEMONSTRATION))",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    /**
     * IMPROVED_COMPONENT_PATTERN:
     * A more reliable pattern for extracting component content, especially for multiline content
     * Group 1: Component type (SCRIPT, VISUAL, NOTES, DEMONSTRATION)
     * Group 2: Newline character(s)
     * Group 3: Component content (everything until the next component or end of input)
     */
    public static final Pattern IMPROVED_COMPONENT_PATTERN = Pattern.compile(
            "^######\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*$(\\r?\\n|\\r)(.*?)(?=\\r?\\n######\\s+(?:SCRIPT|VISUAL|NOTES|DEMONSTRATION)|\\Z)",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    // Table structure pattern - remains basic as it's not the focus of current issues.
    // Proper Markdown table parsing is complex and usually requires more than a single regex.
    public static final Pattern TABLE_PATTERN = Pattern.compile("\\|(.+?)\\|", Pattern.DOTALL);

    // Private constructor to prevent instantiation
    private MarkdownPatterns() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}