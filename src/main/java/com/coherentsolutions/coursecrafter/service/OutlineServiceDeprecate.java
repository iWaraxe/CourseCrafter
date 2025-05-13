package com.coherentsolutions.coursecrafter.service;

import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutlineServiceDeprecate {

    private final CourseContentRepository repo;

    public String outline() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(CourseContent::getPath))
                .map(c -> "- " + c.getPath() + " :: " + c.getTitle())
                .collect(Collectors.joining("\n"));
    }
}
