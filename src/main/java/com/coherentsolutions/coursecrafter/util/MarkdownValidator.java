package com.coherentsolutions.coursecrafter.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownValidator {
    public static void validateFiles(Path dirPath) throws IOException {
        Files.list(dirPath)
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        validateContent(content, path.getFileName().toString());
                    } catch (Exception e) {
                        System.err.println("Error processing file: " + path);
                        e.printStackTrace();
                    }
                });
    }

    private static void validateContent(String content, String filename) {
        // Check course heading
        if (!Pattern.compile("^# ([^\\n]+)", Pattern.MULTILINE).matcher(content).find()) {
            System.err.println(filename + ": Missing course heading (# Course)");
        }

        // Check lecture headings
        if (!Pattern.compile("^## ([^\\n]+)", Pattern.MULTILINE).matcher(content).find()) {
            System.err.println(filename + ": Missing lecture headings (## Lecture)");
        }

        // Check section format consistency
        Matcher sectionMatcher = Pattern.compile("^### ([^\\n]+)", Pattern.MULTILINE).matcher(content);
        while (sectionMatcher.find()) {
            String sectionTitle = sectionMatcher.group(1);
            if (!sectionTitle.matches("\\d+\\.\\s+.+")) {
                System.err.println(filename + ": Non-standard section format: " + sectionTitle);
            }
        }

        // Check slide format
        Matcher slideMatcher = Pattern.compile("##### \\[seq:(\\d+)\\] ([^\\n]+)", Pattern.MULTILINE).matcher(content);
        while (slideMatcher.find()) {
            String seqNumber = slideMatcher.group(1);
            if (seqNumber.length() != 3) {
                System.err.println(filename + ": Invalid slide sequence number (should be 3 digits): " + seqNumber);
            }
        }
    }
}