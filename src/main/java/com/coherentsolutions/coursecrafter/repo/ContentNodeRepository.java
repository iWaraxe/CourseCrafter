package com.coherentsolutions.coursecrafter.repo;

import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.model.ContentVersion;
import com.coherentsolutions.coursecrafter.model.SlideComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}