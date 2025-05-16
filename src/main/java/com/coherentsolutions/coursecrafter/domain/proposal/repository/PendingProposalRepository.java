// In a new file: src/main/java/com/coherentsolutions/coursecrafter/domain/proposal/repository/PendingProposalRepository.java
package com.coherentsolutions.coursecrafter.domain.proposal.repository;

import com.coherentsolutions.coursecrafter.domain.proposal.model.PendingProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingProposalRepository extends JpaRepository<PendingProposal, Long> {
    Optional<PendingProposal> findByBranchName(String branchName);
    Optional<PendingProposal> findByStatus(String status);
}