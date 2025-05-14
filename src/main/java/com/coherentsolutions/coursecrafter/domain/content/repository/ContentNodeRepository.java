package com.coherentsolutions.coursecrafter.domain.content.repository;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentNodeRepository extends JpaRepository<ContentNode, Long> {
    List<ContentNode> findByParentIsNullOrderByDisplayOrder();

    List<ContentNode> findByParentIdOrderByDisplayOrder(Long parentId);

    @Query("SELECT cn FROM ContentNode cn WHERE cn.path LIKE :pathPattern")
    List<ContentNode> findByPathPattern(String pathPattern);

    List<ContentNode> findByNodeType(ContentNode.NodeType nodeType);

    @Query("SELECT cn FROM ContentNode cn LEFT JOIN FETCH cn.children WHERE cn.id = :id")
    Optional<ContentNode> findByIdWithChildren(Long id);

    @Query("SELECT cn FROM ContentNode cn WHERE cn.nodeType = :nodeType AND cn.parent.id = :parentId")
    List<ContentNode> findByNodeTypeAndParentId(ContentNode.NodeType nodeType, Long parentId);

    @Query("SELECT cn FROM ContentNode cn LEFT JOIN FETCH cn.versions WHERE cn.nodeType = :nodeType")
    List<ContentNode> findByNodeTypeWithVersions(@Param("nodeType") ContentNode.NodeType nodeType);

    /**
     * Find all slides under a lecture (directly or through sections/topics)
     */
    @Query("SELECT n FROM ContentNode n WHERE n.nodeType = 'SLIDE' AND " +
            "(n.parent.id = :lectureId OR " +
            "n.parent.parent.id = :lectureId OR " +
            "n.parent.parent.parent.id = :lectureId) " +
            "ORDER BY n.displayOrder")
    List<ContentNode> findSlidesUnderLecture(@Param("lectureId") Long lectureId);

    /**
     * Find previous slide based on display order
     */
    @Query("SELECT n FROM ContentNode n WHERE n.nodeType = 'SLIDE' AND " +
            "n.displayOrder < :currentOrder AND " +
            "(n.parent.id = :parentId OR " +
            "n.parent.parent.id = :parentId OR " +
            "n.parent.parent.parent.id = :parentId) " +
            "ORDER BY n.displayOrder DESC LIMIT 1")
    Optional<ContentNode> findPreviousSlide(
            @Param("currentOrder") Integer currentOrder,
            @Param("parentId") Long parentId);

    /**
     * Find next slide based on display order
     */
    @Query("SELECT n FROM ContentNode n WHERE n.nodeType = 'SLIDE' AND " +
            "n.displayOrder > :currentOrder AND " +
            "(n.parent.id = :parentId OR " +
            "n.parent.parent.id = :parentId OR " +
            "n.parent.parent.parent.id = :parentId) " +
            "ORDER BY n.displayOrder ASC LIMIT 1")
    Optional<ContentNode> findNextSlide(
            @Param("currentOrder") Integer currentOrder,
            @Param("parentId") Long parentId);

    @Query("SELECT n FROM ContentNode n LEFT JOIN FETCH n.versions WHERE n.nodeType = 'SLIDE'")
    List<ContentNode> findSlidesWithVersions();
}