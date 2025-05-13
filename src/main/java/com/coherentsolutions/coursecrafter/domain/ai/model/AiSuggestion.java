package com.coherentsolutions.coursecrafter.domain.ai.model;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_suggestion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "node_id")
    private ContentNode node;

    private String suggestionType;  // CONTENT_UPDATE, NEW_NODE, etc.

    @Lob @Column(columnDefinition = "text")
    private String originalContent;

    @Lob @Column(columnDefinition = "text")
    private String suggestedContent;

    @Lob @Column(columnDefinition = "text")
    private String rationale;

    private String status;  // PENDING, APPROVED, REJECTED, etc.
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
}