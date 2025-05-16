package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter; // Keep if SectionInfo is used, otherwise remove
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator; // Keep if SectionInfo is used, otherwise remove
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkdownCourseParser {

    private final ContentNodeRepository contentNodeRepository;
    private final ContentNodeService contentNodeService;
    private final SlideComponentService slideComponentService;

    /**
     * Parse a markdown file and create the entire course hierarchy
     */
    public void parseFile(Path filePath) throws IOException, InterruptedException {
        log.info("Parsing markdown file: {}", filePath.getFileName());
        String fullFileContent = Files.readString(filePath);

        log.debug("==================== FILE CONTENT (First 500 chars) ====================");
        log.debug(fullFileContent.substring(0, Math.min(fullFileContent.length(), 500)));
        log.debug("========================================================================");

        // Parse course
        Matcher courseMatcher = MarkdownPatterns.COURSE_PATTERN.matcher(fullFileContent);
        if (!courseMatcher.find()) {
            log.error("No course (H1) found in file: {}", filePath);
            return;
        }
        String courseTitle = courseMatcher.group(1).trim();
        ContentNode courseNode = getOrCreateCourseNode(courseTitle, courseMatcher.group(0)); // group(0) is the full H1 line

        // Content for lectures is everything after the H1 course title
        String lecturesAndBelowContent = fullFileContent.substring(courseMatcher.end());
        parseLectures(lecturesAndBelowContent, courseNode);

        // Handle slides directly under the course (if any, after all lectures are processed)
        String directCourseContent = extractContentNotCoveredByChildren(
                lecturesAndBelowContent, MarkdownPatterns.LECTURE_PATTERN
        );
        if (!directCourseContent.trim().isEmpty()) {
            parseDirectSlides(directCourseContent, courseNode, "course");
        }
    }

    private ContentNode getOrCreateCourseNode(String courseTitle, String courseMarkdownContent) throws IOException, InterruptedException {
        Optional<ContentNode> existingCourse = contentNodeRepository.findByNodeTypeAndTitle(ContentNode.NodeType.COURSE, courseTitle);
        if (existingCourse.isPresent()) {
            log.info("Found existing course: {} (ID: {})", courseTitle, existingCourse.get().getId());
            return existingCourse.get();
        }

        log.info("Creating new course: {}", courseTitle);
        ContentNode courseNode = ContentNode.builder()
                .nodeType(ContentNode.NodeType.COURSE)
                .title(courseTitle)
                .displayOrder(1)
                .path("Course/" + UUID.randomUUID().toString().substring(0, 8))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return contentNodeService.createNode(courseNode, courseMarkdownContent, "Initial course creation");
    }

    private String extractContentNotCoveredByChildren(String parentContent, Pattern childHeaderPattern) {
        StringBuilder remainingContent = new StringBuilder();
        Matcher childMatcher = childHeaderPattern.matcher(parentContent);
        int lastChildEnd = 0;

        List<MatchResult> childMatches = childMatcher.results().collect(Collectors.toList());

        for (int i = 0; i < childMatches.size(); i++) {
            MatchResult currentChildMatch = childMatches.get(i);
            if (currentChildMatch.start() > lastChildEnd) {
                remainingContent.append(parentContent, lastChildEnd, currentChildMatch.start());
            }

            int currentChildBlockEnd;
            if (i + 1 < childMatches.size()) {
                currentChildBlockEnd = childMatches.get(i + 1).start();
            } else {
                currentChildBlockEnd = parentContent.length();
            }
            lastChildEnd = currentChildBlockEnd;
        }

        if (lastChildEnd < parentContent.length()) {
            remainingContent.append(parentContent.substring(lastChildEnd));
        }
        return remainingContent.toString().trim();
    }


    private void parseLectures(String courseContent, ContentNode courseNode) throws IOException, InterruptedException {
        List<Block> lectureBlocks = extractBlocks(courseContent, MarkdownPatterns.LECTURE_PATTERN);

        for (Block lectureBlock : lectureBlocks) {
            String lectureTitle = lectureBlock.getTitle().trim();
            log.debug("Processing Lecture Title: {}", lectureTitle);

            int lectureNumber = extractLectureNumber(lectureTitle);
            int displayOrder = lectureNumber * 10;

            ContentNode lectureNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.LECTURE)
                    .parent(courseNode)
                    .title(lectureTitle)
                    .displayOrder(displayOrder)
                    .path(courseNode.getPath() + "/Lecture/" + lectureNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            lectureNode = contentNodeService.createNode(lectureNode, lectureBlock.getFullBlockContent(), "Created lecture: " + lectureTitle);
            log.info("Created lecture: {} with order {}", lectureTitle, displayOrder);

            parseSections(lectureBlock.getContentWithinBlock(), lectureNode);

            String directLectureContent = extractContentNotCoveredByChildren(
                    lectureBlock.getContentWithinBlock(), MarkdownPatterns.SECTION_PATTERN
            );
            if (!directLectureContent.trim().isEmpty()) {
                parseDirectSlides(directLectureContent, lectureNode, "lecture");
            }
        }
    }

    private int extractLectureNumber(String lectureTitle) {
        Pattern pattern = Pattern.compile("Lecture (\\d+)\\.?");
        Matcher matcher = pattern.matcher(lectureTitle);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        log.warn("Could not extract lecture number from title: '{}'. Defaulting to 1.", lectureTitle);
        return 1;
    }

    private void parseSections(String lectureContent, ContentNode lectureNode) throws IOException, InterruptedException {
        List<Block> sectionBlocks = extractBlocks(lectureContent, MarkdownPatterns.SECTION_PATTERN);
        int sectionOrder = 10;

        for (Block sectionBlock : sectionBlocks) {
            String sectionTitle = sectionBlock.getTitle().trim();
            log.debug("Processing Section Title: {}", sectionTitle);

            String sectionNumberStr = extractNumericPrefix(sectionTitle, "\\d+(?:\\.\\d+)*");
            if (sectionNumberStr.isEmpty()) {
                sectionNumberStr = String.valueOf(sectionOrder / 10);
            }

            String normalizedTitle = normalizeTitle(sectionTitle);
            if (isDuplicateChild(lectureNode, normalizedTitle, ContentNode.NodeType.SECTION)) {
                log.info("Skipping duplicate section: {}", sectionTitle);
                continue;
            }

            String lectureNumberFromPath = extractNumberFromPathSegment(lectureNode.getPath(), "/Lecture/(\\d+)");

            ContentNode sectionNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SECTION)
                    .parent(lectureNode)
                    .title(sectionTitle)
                    .nodeNumber(lectureNumberFromPath + "." + sectionNumberStr)
                    .displayOrder(sectionOrder)
                    .path(lectureNode.getPath() + "/Section/" + sectionNumberStr.replace(".", "_"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            sectionNode = contentNodeService.createNode(sectionNode, sectionBlock.getFullBlockContent(), "Created section: " + sectionTitle);
            log.info("Created section: '{}' with order {} and number {}", sectionTitle, sectionOrder, sectionNode.getNodeNumber());

            parseTopics(sectionBlock.getContentWithinBlock(), sectionNode);

            String directSectionContent = extractContentNotCoveredByChildren(
                    sectionBlock.getContentWithinBlock(), MarkdownPatterns.TOPIC_PATTERN
            );
            if (!directSectionContent.trim().isEmpty()) {
                parseDirectSlides(directSectionContent, sectionNode, "section");
            }
            sectionOrder += 10;
        }
    }

    private void parseTopics(String sectionContent, ContentNode sectionNode) throws IOException, InterruptedException {
        List<Block> topicBlocks = extractBlocks(sectionContent, MarkdownPatterns.TOPIC_PATTERN);
        int topicOrder = 10;

        for (Block topicBlock : topicBlocks) {
            String topicTitle = topicBlock.getTitle().trim();
            log.debug("Processing Topic Title: {}", topicTitle);

            String topicNumberStr = extractNumericPrefix(topicTitle, "\\d+(?:\\.\\d+){1,}");
            String baseSectionNodeNumber = sectionNode.getNodeNumber() != null ? sectionNode.getNodeNumber() : "0"; // Fallback for sectionNodeNumber

            if (topicNumberStr.isEmpty()) {
                topicNumberStr = baseSectionNodeNumber + "." + (topicOrder / 10);
            } else if (!topicNumberStr.startsWith(baseSectionNodeNumber + ".")) {
                // If topic number "1.1" and section is "2", this might be an issue.
                // For "1.1" from title "1.1. Brief History" under section "1. Introduction",
                // if sectionNodeNumber is "1", then topicNumberStr might become "1.1" directly.
                // If title is "1. Brief History" (missing sub-number) under section "1.1", make it "1.1.1"
                Pattern sectionPrefixPattern = Pattern.compile("^" + Pattern.quote(baseSectionNodeNumber) + "\\.");
                if (!sectionPrefixPattern.matcher(topicNumberStr).find()) {
                    // If the topic number from title doesn't already include the full section prefix
                    topicNumberStr = baseSectionNodeNumber + "." + topicNumberStr;
                }
            }


            String normalizedTitle = normalizeTitle(topicTitle);
            if (isDuplicateChild(sectionNode, normalizedTitle, ContentNode.NodeType.TOPIC)) {
                log.info("Skipping duplicate topic: {}", topicTitle);
                continue;
            }

            ContentNode topicNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.TOPIC)
                    .parent(sectionNode)
                    .title(topicTitle)
                    .nodeNumber(topicNumberStr)
                    .displayOrder(topicOrder)
                    .path(sectionNode.getPath() + "/Topic/" + topicNumberStr.replace(".", "_"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            topicNode = contentNodeService.createNode(topicNode, topicBlock.getFullBlockContent(), "Created topic: " + topicTitle);
            log.info("Created topic: '{}' with order {} and number {}", topicTitle, topicOrder, topicNode.getNodeNumber());

            parseSlides(topicBlock.getContentWithinBlock(), topicNode);

            String directTopicContent = extractContentNotCoveredByChildren(
                    topicBlock.getContentWithinBlock(), MarkdownPatterns.SLIDE_PATTERN
            );
            if(!directTopicContent.trim().isEmpty()){
                log.debug("Topic '{}' has direct content not part of any slide: {}", topicTitle, directTopicContent.substring(0, Math.min(directTopicContent.length(), 50)));
            }
            topicOrder += 10;
        }
    }


    private void parseSlides(String parentBlockContent, ContentNode parentNode) throws IOException, InterruptedException { // MODIFIED: param name
        Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(parentBlockContent); // MODIFIED: use parentBlockContent
        log.debug("Parsing slides for parent: {} ({}) within content length: {}", parentNode.getTitle(), parentNode.getNodeType(), parentBlockContent.length());

        while (slideMatcher.find()) {
            String seqNumber = slideMatcher.group(1).trim();
            String slideTitle = slideMatcher.group(2).trim();
            String slideBodyContent = slideMatcher.group(3) != null ? slideMatcher.group(3).trim() : "";
            String fullSlideMarkdown = slideMatcher.group(0).trim();

            log.debug("Found Slide candidate: seq={}, title='{}', content length={}", seqNumber, slideTitle, slideBodyContent.length());

            int displayOrder = Integer.parseInt(seqNumber);

            // CHANGED: Use findByParentIdOrderByDisplayOrder
            boolean slideExists = contentNodeRepository.findByParentIdOrderByDisplayOrder(parentNode.getId()).stream()
                    .anyMatch(existingSlide -> existingSlide.getNodeType() == ContentNode.NodeType.SLIDE &&
                            existingSlide.getTitle().equals(slideTitle) &&
                            existingSlide.getDisplayOrder() == displayOrder);

            if (slideExists) {
                log.info("Slide '{}' with sequence {} under parent '{}' already exists, skipping.", slideTitle, seqNumber, parentNode.getTitle());
                continue;
            }

            String slideNodeNumber;
            String parentNodeNumber = parentNode.getNodeNumber();
            if (parentNodeNumber != null && !parentNodeNumber.isEmpty()) {
                slideNodeNumber = parentNodeNumber + "." + seqNumber;
            } else {
                String parentTypePrefix = parentNode.getNodeType().toString().substring(0,1);
                slideNodeNumber = parentTypePrefix + "Implicit." + seqNumber;
                log.warn("Parent node {} for slide {} has no nodeNumber. Using fallback: {}", parentNode.getTitle(), slideTitle, slideNodeNumber);
            }

            ContentNode slideNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SLIDE)
                    .parent(parentNode)
                    .title(slideTitle)
                    .nodeNumber(slideNodeNumber)
                    .displayOrder(displayOrder)
                    .path(parentNode.getPath() + "/Slide/" + seqNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            try {
                slideNode = contentNodeService.createNode(slideNode, fullSlideMarkdown, "Created slide: " + slideTitle);
                log.info("Created slide: '{}' with sequence {} and number {}", slideTitle, seqNumber, slideNode.getNodeNumber());
                processSlideComponents(slideBodyContent, slideNode);
            } catch (Exception e) {
                log.error("Failed to create slide '{}' or its components: {}", slideTitle, e.getMessage(), e);
            }
        }
    }

    private void processSlideComponents(String slideBodyContent, ContentNode slideNode) {
        if (slideBodyContent == null || slideBodyContent.trim().isEmpty()) {
            log.debug("No body content to process for components in slide: {}", slideNode.getTitle());
            return;
        }
        log.debug("Processing components for slide: '{}' with content length: {}", slideNode.getTitle(), slideBodyContent.length());
        Matcher componentMatcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(slideBodyContent);
        int componentCount = 0;

        while (componentMatcher.find()) {
            componentCount++;
            String componentTypeStr = componentMatcher.group(1).trim().toUpperCase();
            String componentContent = componentMatcher.group(2) != null ? componentMatcher.group(2).trim() : "";

            log.debug("Found component candidate: type='{}', content length={}", componentTypeStr, componentContent.length());

            SlideComponent.ComponentType componentType;
            try {
                componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown component type: '{}' in slide: {}. Skipping component.", componentTypeStr, slideNode.getTitle());
                continue;
            }

            try {
                slideComponentService.createComponent(slideNode.getId(), componentType, componentContent);
                log.info("Created {} component for slide: {}", componentType, slideNode.getTitle());
            } catch (Exception e) {
                log.error("Failed to create component {} for slide {}: {}", componentTypeStr, slideNode.getTitle(), e.getMessage(), e);
            }
        }
        if (componentCount == 0) {
            log.debug("No H6 components found in slide '{}'. Slide body length: {}", slideNode.getTitle(), slideBodyContent.length());
            if (!slideBodyContent.trim().isEmpty() && !slideBodyContent.contains("######")) {
                log.warn("Slide '{}' has content but no H6 components. This content might be lost if not stored elsewhere: '{}'",
                        slideNode.getTitle(), slideBodyContent.substring(0, Math.min(slideBodyContent.length(),100)));
            }
        } else {
            log.debug("Found {} components in slide '{}'", componentCount, slideNode.getTitle());
        }
    }

    private void parseDirectSlides(String directParentContent, ContentNode parentNode, String parentContext) throws IOException, InterruptedException {
        if (directParentContent == null || directParentContent.trim().isEmpty()) {
            return;
        }
        log.debug("Parsing direct slides for parent {} ({}) in context '{}' with content length: {}",
                parentNode.getTitle(), parentNode.getNodeType(), parentContext, directParentContent.length());

        Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(directParentContent);
        List<SlideData> directSlidesData = new ArrayList<>();

        while (slideMatcher.find()) {
            String seqNumber = slideMatcher.group(1).trim();
            String slideTitle = slideMatcher.group(2).trim();
            String slideBodyContent = slideMatcher.group(3) != null ? slideMatcher.group(3).trim() : "";
            directSlidesData.add(new SlideData(slideTitle, seqNumber, slideBodyContent, slideMatcher.group(0).trim()));
        }

        if (!directSlidesData.isEmpty()) {
            log.info("Found {} direct slides under {} ({}) in context '{}' that need implicit containers",
                    directSlidesData.size(), parentNode.getTitle(), parentNode.getNodeType(), parentContext);

            ContentNode actualParentForDirectSlides = createImplicitContainersIfNeeded(parentNode);

            for (SlideData slideData : directSlidesData) {
                processSingleDirectSlide(actualParentForDirectSlides, slideData);
            }
        } else {
            log.debug("No direct slides found for parent {} ({}) in context '{}'", parentNode.getTitle(), parentNode.getNodeType(), parentContext);
        }
    }

    private ContentNode createImplicitContainersIfNeeded(ContentNode parentNode) throws IOException, InterruptedException {
        ContentNode currentParent = parentNode;

        if (currentParent.getNodeType() == ContentNode.NodeType.COURSE ||
                currentParent.getNodeType() == ContentNode.NodeType.LECTURE) {
            currentParent = getOrCreateImplicitChild(currentParent, ContentNode.NodeType.SECTION, "Implicit Section", "/Section/implicit_section");
        }

        if (currentParent.getNodeType() != ContentNode.NodeType.TOPIC) {
            currentParent = getOrCreateImplicitChild(currentParent, ContentNode.NodeType.TOPIC, "Implicit Topic", "/Topic/implicit_topic");
        }
        return currentParent;
    }

    private ContentNode getOrCreateImplicitChild(ContentNode parent, ContentNode.NodeType childType, String childTitle, String pathSegment) throws IOException, InterruptedException {
        // CHANGED: Use findByParentIdOrderByDisplayOrder
        Optional<ContentNode> existingImplicitChild = contentNodeRepository.findByParentIdOrderByDisplayOrder(parent.getId()).stream()
                .filter(node -> node.getNodeType() == childType && node.getTitle().equals(childTitle))
                .findFirst();

        if (existingImplicitChild.isPresent()) {
            log.debug("Using existing implicit {}: {} (ID: {})", childType, childTitle, existingImplicitChild.get().getId());
            return existingImplicitChild.get();
        }

        // CHANGED: Use findByParentIdOrderByDisplayOrder
        long existingChildrenOfTypeCount = contentNodeRepository.findByParentIdOrderByDisplayOrder(parent.getId()).stream()
                .filter(n -> n.getNodeType() == childType).count();
        int displayOrder = (int) (existingChildrenOfTypeCount) * 10 + 5; // Implicit nodes get order like 5, 15, 25...

        String childNodeNumber;
        String parentNodeNumber = parent.getNodeNumber();
        if (parentNodeNumber != null && !parentNodeNumber.isEmpty()) {
            childNodeNumber = parentNodeNumber + ".implicit";
        } else {
            childNodeNumber = "Implicit." + childType.toString().toLowerCase();
        }

        ContentNode implicitChild = ContentNode.builder()
                .nodeType(childType)
                .parent(parent)
                .title(childTitle)
                .nodeNumber(childNodeNumber)
                .displayOrder(displayOrder)
                .path(parent.getPath() + pathSegment)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        implicitChild = contentNodeService.createNode(implicitChild,
                "###### " + childType.toString() + "\nAuto-generated " + childType.toString().toLowerCase() + " for slides without explicit parent.",
                "Created implicit " + childType.toString().toLowerCase());
        log.info("Created implicit {}: {} under {}", childType, childTitle, parent.getTitle());
        return implicitChild;
    }

    private void processSingleDirectSlide(ContentNode parentForSlide, SlideData slideData) throws IOException, InterruptedException {
        int displayOrder = Integer.parseInt(slideData.getSeqNumber());

        // CHANGED: Use findByParentIdOrderByDisplayOrder
        boolean slideExists = contentNodeRepository.findByParentIdOrderByDisplayOrder(parentForSlide.getId()).stream()
                .anyMatch(existingSlide -> existingSlide.getNodeType() == ContentNode.NodeType.SLIDE &&
                        existingSlide.getTitle().equals(slideData.getTitle()) &&
                        existingSlide.getDisplayOrder() == displayOrder);
        if (slideExists) {
            log.info("Direct slide '{}' with sequence {} under parent '{}' already exists, skipping.",
                    slideData.getTitle(), slideData.getSeqNumber(), parentForSlide.getTitle());
            return;
        }

        String slideNodeNumber;
        String parentNodeNumber = parentForSlide.getNodeNumber();
        if (parentNodeNumber != null && !parentNodeNumber.isEmpty()) {
            slideNodeNumber = parentNodeNumber + "." + slideData.getSeqNumber();
        } else {
            slideNodeNumber = "ImplicitParent." + slideData.getSeqNumber();
        }

        ContentNode slideNode = ContentNode.builder()
                .nodeType(ContentNode.NodeType.SLIDE)
                .parent(parentForSlide)
                .title(slideData.getTitle())
                .nodeNumber(slideNodeNumber)
                .displayOrder(displayOrder)
                .path(parentForSlide.getPath() + "/Slide/" + slideData.getSeqNumber())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        try {
            slideNode = contentNodeService.createNode(slideNode, slideData.getFullMarkdown(), "Created direct slide: " + slideData.getTitle());
            log.info("Created direct slide: '{}' with sequence {} under {}", slideData.getTitle(), slideData.getSeqNumber(), parentForSlide.getTitle());
            processSlideComponents(slideData.getBodyContent(), slideNode);
        } catch (Exception e) {
            log.error("Error creating direct slide {}: {}", slideData.getTitle(), e.getMessage(), e);
        }
    }

    private List<Block> extractBlocks(String parentContent, Pattern blockPattern) { // MODIFIED: Removed siblingBlockPattern
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = blockPattern.matcher(parentContent);
        List<MatchResult> matches = matcher.results().collect(Collectors.toList());

        for (int i = 0; i < matches.size(); i++) {
            MatchResult currentMatch = matches.get(i);
            String title = currentMatch.group(1).trim();

            int contentStartOffset = currentMatch.end();
            int blockEndOffset; // End of the full block (including header of next block)

            if (i + 1 < matches.size()) {
                blockEndOffset = matches.get(i + 1).start();
            } else {
                blockEndOffset = parentContent.length();
            }

            String fullBlockContent = parentContent.substring(currentMatch.start(), blockEndOffset).trim();
            String contentWithinBlock = parentContent.substring(contentStartOffset, blockEndOffset).trim(); // Content for children is up to next sibling's header
            blocks.add(new Block(title, contentWithinBlock, fullBlockContent));
        }
        return blocks;
    }

    @Getter
    private static class Block {
        private final String title;
        private final String contentWithinBlock;
        private final String fullBlockContent;

        public Block(String title, String contentWithinBlock, String fullBlockContent) {
            this.title = title;
            this.contentWithinBlock = contentWithinBlock;
            this.fullBlockContent = fullBlockContent;
        }
    }

    private String extractNumericPrefix(String title, String numberPatternRegex) {
        Pattern pattern = Pattern.compile("^(" + numberPatternRegex + ")\\.?\\s+");
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractNumberFromPathSegment(String path, String segmentPatternRegex) {
        if (path == null) return "0";
        Pattern pattern = Pattern.compile(segmentPatternRegex);
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        log.warn("Could not extract number from path '{}' using pattern '{}'. Defaulting to 0.", path, segmentPatternRegex);
        return "0";
    }

    private boolean isDuplicateChild(ContentNode parentNode, String normalizedChildTitle, ContentNode.NodeType childType) {
        // CHANGED: Use findByParentIdOrderByDisplayOrder
        return contentNodeRepository.findByParentIdOrderByDisplayOrder(parentNode.getId()).stream()
                .anyMatch(child -> child.getNodeType() == childType &&
                        normalizeTitle(child.getTitle()).equals(normalizedChildTitle));
    }

    // SectionInfo might not be needed anymore. If not, remove its imports (Comparator, AllArgsConstructor, Setter)
    /*
    @Getter
    @Setter
    @RequiredArgsConstructor
    @AllArgsConstructor
    private static class SectionInfo {
        private final int position;
        private int endPosition;
        private final String title;
        private final ContentNode.NodeType nodeType;
    }
    */

    @Getter
    private static class SlideData {
        private final String title;
        private final String seqNumber;
        private final String bodyContent;
        private final String fullMarkdown;

        SlideData(String title, String seqNumber, String bodyContent, String fullMarkdown) {
            this.title = title;
            this.seqNumber = seqNumber;
            this.bodyContent = bodyContent;
            this.fullMarkdown = fullMarkdown;
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("^\\d+(\\.\\d+)*\\.?\\s*", "").trim();
    }
}