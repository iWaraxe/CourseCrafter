package com.coherentsolutions.coursecrafter.domain.slide.model;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "slide_component")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideComponent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "slide_node_id")
    private ContentNode slideNode;

    @Enumerated(EnumType.STRING)
    private ComponentType componentType;

    @Lob
    @Column(columnDefinition = "text") // This is good for schema generation
    private String content;

    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ComponentType {
        SCRIPT, VISUAL, NOTES, DEMONSTRATION
    }
}