// In a new file: src/main/java/com/coherentsolutions/coursecrafter/domain/proposal/model/PendingProposal.java
package com.coherentsolutions.coursecrafter.domain.proposal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_proposal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingProposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String branchName;  // Git branch where this proposal exists
    private String prUrl;       // URL to the PR (if created)

    @Lob @Column(columnDefinition = "text")
    private String proposalJson; // JSON representation of the proposals

    private String status;      // PENDING, APPROVED, REJECTED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}