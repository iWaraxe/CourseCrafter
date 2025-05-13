package com.coherentsolutions.coursecrafter.domain.version.model;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_version")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "node_id")
    private ContentNode node;

    @Lob
    @Column(columnDefinition = "text")
    private String content;

    private String contentFormat;  // MARKDOWN, HTML, etc.
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private String createdBy;
    private String gitCommitSha;
}