package com.coherentsolutions.coursecrafter.service;

import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import com.coherentsolutions.coursecrafter.service.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class CourseContentService {

    private final CourseContentRepository repo;
    private final GitCliService git;   // you already wired this

    @Transactional
    public CourseContent saveAndCommit(CourseContent cc, String commitMsg) throws IOException, InterruptedException {
        CourseContent saved = repo.save(cc);
        git.commitAndPush(featureBranch(), commitMsg);
        return saved;
    }

    private String featureBranch() {
        return "update-" + System.currentTimeMillis();
    }
}