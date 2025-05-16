// In src/main/java/com/coherentsolutions/coursecrafter/application/service/ProposalApprovalService.java
package com.coherentsolutions.coursecrafter.application.service;

import com.coherentsolutions.coursecrafter.domain.proposal.model.PendingProposal;
import com.coherentsolutions.coursecrafter.domain.proposal.repository.PendingProposalRepository;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedUpdaterService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProposalApprovalService {

    private final PendingProposalRepository proposalRepository;
    private final EnhancedUpdaterService updaterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<PendingProposal> findAllPendingProposals() {
        return proposalRepository.findAll();
    }

    @Transactional
    public void applyApprovedProposal(String branchName) throws IOException, InterruptedException {
        // Find the pending proposal
        PendingProposal proposal = proposalRepository.findByBranchName(branchName)
                .orElseThrow(() -> new IllegalArgumentException("No pending proposal found for branch: " + branchName));

        if (!"PENDING".equals(proposal.getStatus())) {
            throw new IllegalStateException("Proposal is not in PENDING state: " + proposal.getStatus());
        }

        // Deserialize the proposals
        List<AiProposalDto> proposalDtos = objectMapper.readValue(
                proposal.getProposalJson(), new TypeReference<List<AiProposalDto>>() {});

        // Now apply the proposals to the database
        updaterService.applyProposals(proposalDtos);

        // Update the proposal status
        proposal.setStatus("APPROVED");
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.save(proposal);

        log.info("Applied approved proposal to database: {}", branchName);
    }

    @Transactional
    public void rejectProposal(String branchName) {
        PendingProposal proposal = proposalRepository.findByBranchName(branchName)
                .orElseThrow(() -> new IllegalArgumentException("No pending proposal found for branch: " + branchName));

        proposal.setStatus("REJECTED");
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.save(proposal);

        log.info("Marked proposal as rejected: {}", branchName);
    }
}