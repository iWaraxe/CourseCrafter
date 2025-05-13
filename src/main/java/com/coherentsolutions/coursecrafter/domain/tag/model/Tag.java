package com.coherentsolutions.coursecrafter.domain.tag.model;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "tag")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String category;  // TOPIC, SKILL_LEVEL, etc.
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "tags")
    private Set<ContentNode> nodes;
}