package com.coherentsolutions.coursecrafter.domain.version.repository;

import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {
    @Query("SELECT cv FROM ContentVersion cv WHERE cv.node.id = :nodeId ORDER BY cv.versionNumber DESC")
    List<ContentVersion> findVersionHistoryByNodeId(Long nodeId);

    @Query("SELECT cv FROM ContentVersion cv WHERE cv.node.id = :nodeId AND cv.versionNumber = " +
            "(SELECT MAX(cv2.versionNumber) FROM ContentVersion cv2 WHERE cv2.node.id = :nodeId)")
    Optional<ContentVersion> findLatestVersionByNodeId(Long nodeId);
}