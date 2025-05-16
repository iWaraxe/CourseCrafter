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
     * This pattern is specifically designed to capture slide headers (H5) and their content.
     * Group 1: Sequence number (e.g., "010")
     * Group 2: Slide title
     * Group 3: Complete slide content, starting from the line after the H5 header,
     *          until the next slide, a higher-level header (H1-H4), a '---' separator, or end of input.
     */
    public static final Pattern SLIDE_PATTERN = Pattern.compile(
            "^#####\\s+\\[seq:(\\d+)\\]\\s*([^\\n]+?)\\s*" + // Group 1: seq, Group 2: title
                    "(?:" + // Non-capturing group for content start options
                    "\\r?\\n(.*?)" +  // Content starts on a new line (Group 3 is here)
                    "|" +             // OR
                    "()" +            // Content is empty or not present (Group 3 will be empty string match)
                    ")?" + // The whole content block is optional
                    "(?=" + // Positive lookahead for terminators
                    "\\Z|" +                                 // End of the current input block (e.g., end of topic content)
                    "|^#####\\s+\\[seq:\\d+\\]|" +           // Start of the next H5 slide
                    "|^\\s*---\\s*$|" +                     // A '---' separator line
                    "|^#{1,4}\\s" +                         // Start of any H1, H2, H3, or H4 heading
                    ")",
            Pattern.MULTILINE | Pattern.DOTALL // MULTILINE for '^', DOTALL for '.' in (.*?)
    );

    /**
     * This pattern matches individual slide components (H6) with their content.
     * It's designed to capture each component type and its content separately.
     * This pattern should operate on the content of a single slide (output of SLIDE_PATTERN group 3).
     * Group 1: Component type (e.g., "SCRIPT", "VISUAL")
     * Group 2: Component content, starting from the line after the H6 header,
     *          until the next component header or end of the slide's content.
     */
    public static final Pattern COMPONENT_PATTERN = Pattern.compile(
            "^######\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*" + // Group 1: component type
                    "(?:" + // Non-capturing group for content start options
                    "\\r?\\n(.*?)" +  // Content starts on a new line (Group 2 is here)
                    "|" +             // OR
                    "()" +            // Content is empty or not present (Group 2 will be empty string match)
                    ")?" + // The whole content block is optional
                    "(?=" + // Positive lookahead for terminators
                    "\\Z|" +                                             // End of the slide content block
                    "|^######\\s+(?:SCRIPT|VISUAL|NOTES|DEMONSTRATION)" + // Start of the next H6 component
                    ")",
            Pattern.MULTILINE | Pattern.DOTALL // MULTILINE for '^', DOTALL for '.' in (.*?)
    );

    // Table structure pattern - remains basic as it's not the focus of current issues.
    // Proper Markdown table parsing is complex and usually requires more than a single regex.
    public static final Pattern TABLE_PATTERN = Pattern.compile("\\|(.+?)\\|", Pattern.DOTALL);

    // Private constructor to prevent instantiation
    private MarkdownPatterns() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}