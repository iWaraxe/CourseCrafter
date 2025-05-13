// /ingest/text  /ingest/url  /ingest/youtube
package com.coherentsolutions.coursecrafter.controller;

import com.coherentsolutions.coursecrafter.dto.IngestionRequest;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.CourseContentServiceDeprecate;
import com.coherentsolutions.coursecrafter.service.ingest.TextIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final CourseContentServiceDeprecate service;
    private final TextIngestionService textIngestionService;

    @PostMapping("/text")
    public CourseContent ingestText(@RequestBody IngestionRequest dto) throws IOException, InterruptedException {
        CourseContent cc = CourseContent.builder()
                .level("SLIDE")
                .title("Raw upload")
                .markdown(dto.payload())
                .path("draft/" + UUID.randomUUID())
                .build();

        return service.saveAndCommit(cc, "Ingest raw text: " + cc.getPath());
    }

    @PostMapping("/review")
    public List<CourseContent> review(@RequestBody String rawMarkdown)
            throws IOException, InterruptedException {

        return textIngestionService.reviewAndApply(rawMarkdown);
    }
}