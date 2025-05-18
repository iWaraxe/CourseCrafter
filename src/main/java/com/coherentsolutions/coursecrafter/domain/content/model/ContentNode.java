package com.coherentsolutions.coursecrafter.domain.content.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "content_node")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ContentNode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @OrderBy("displayOrder")
    private List<ContentNode> children;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType;

    private String title;
    private String description;

    @Lob
    @Column(columnDefinition = "text")
    private String markdownContent; // Stores the latest/current markdown for this node

    private Integer displayOrder;
    private String nodeNumber;  // 1.1.2, etc.

    // Path provides quick hierarchical access (e.g., "Course/Lecture1/Section2")
    private String path;

    // Properly define metadataJson as a JSONB column
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson;  // Flexible extra attributes

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "slideNode", cascade = CascadeType.ALL)
    private List<SlideComponent> slideComponents;

    @ManyToMany
    @JoinTable(
            name = "node_tag",
            joinColumns = @JoinColumn(name = "node_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<com.coherentsolutions.coursecrafter.domain.tag.model.Tag> tags;

    public enum NodeType {
        COURSE, MODULE, LECTURE, SECTION, TOPIC, SLIDE
    }
}