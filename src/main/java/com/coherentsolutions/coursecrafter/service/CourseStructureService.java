package com.coherentsolutions.coursecrafter.service;

import com.coherentsolutions.coursecrafter.dto.CourseStructureDto;
import com.coherentsolutions.coursecrafter.dto.LectureDto;
import com.coherentsolutions.coursecrafter.dto.SectionDto;
import com.coherentsolutions.coursecrafter.dto.SlideDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseStructureService {

    private final CourseContentRepository repo;

    /**
     * Generates a complete hierarchical structure of the course
     */
    public CourseStructureDto getCourseStructure(String courseName) {
        List<CourseContent> allContent = repo.findAllByCourseName(courseName);

        // Group by level
        Map<String, List<CourseContent>> contentByLevel = allContent.stream()
                .collect(Collectors.groupingBy(CourseContent::getLevel));

        // Get lectures (top level)
        List<LectureDto> lectures = contentByLevel.getOrDefault("LECTURE", List.of()).stream()
                .sorted(Comparator.comparing(CourseContent::getPath))
                .map(lecture -> {
                    // Get sections for this lecture
                    List<SectionDto> sections = contentByLevel.getOrDefault("SECTION", List.of()).stream()
                            .filter(section -> section.getParentId().equals(lecture.getId()))
                            .sorted(Comparator.comparing(CourseContent::getPath))
                            .map(section -> {
                                // Get slides for this section
                                List<SlideDto> slides = contentByLevel.getOrDefault("SLIDE", List.of()).stream()
                                        .filter(slide -> slide.getParentId().equals(section.getId()))
                                        .sorted(Comparator.comparing(CourseContent::getPath))
                                        .map(slide -> new SlideDto(
                                                slide.getId(),
                                                slide.getTitle(),
                                                slide.getMarkdown(),
                                                slide.getPath()))
                                        .collect(Collectors.toList());

                                return new SectionDto(
                                        section.getId(),
                                        section.getTitle(),
                                        section.getPath(),
                                        slides);
                            })
                            .collect(Collectors.toList());

                    return new LectureDto(
                            lecture.getId(),
                            lecture.getTitle(),
                            lecture.getPath(),
                            sections);
                })
                .collect(Collectors.toList());

        return new CourseStructureDto(courseName, lectures);
    }

    /**
     * Converts course structure into a formatted text for LLM prompt
     */
    public String getCourseOutlineText(String courseName) {
        CourseStructureDto structure = getCourseStructure(courseName);
        StringBuilder builder = new StringBuilder();

        builder.append("# Course: ").append(structure.name()).append("\n\n");

        for (LectureDto lecture : structure.lectures()) {
            builder.append("## Lecture: ").append(lecture.title())
                    .append(" (ID: ").append(lecture.id()).append(")\n");

            for (SectionDto section : lecture.sections()) {
                builder.append("### Section: ").append(section.title())
                        .append(" (ID: ").append(section.id()).append(")\n");

                for (SlideDto slide : section.slides()) {
                    builder.append("#### Slide: ").append(slide.title())
                            .append(" (ID: ").append(slide.id()).append(")\n");
                    builder.append("```markdown\n").append(slide.markdown()).append("\n```\n\n");
                }
                builder.append("\n");
            }
            builder.append("\n");
        }

        return builder.toString();
    }
}