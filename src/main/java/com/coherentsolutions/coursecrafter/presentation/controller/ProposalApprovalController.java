// In src/main/java/com/coherentsolutions/coursecrafter/presentation/controller/ProposalApprovalController.java
package com.coherentsolutions.coursecrafter.presentation.controller;

import com.coherentsolutions.coursecrafter.application.service.ProposalApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalApprovalController {

    private final ProposalApprovalService approvalService;

    @GetMapping
    public ResponseEntity<?> getPendingProposals() {
        return ResponseEntity.ok(approvalService.findAllPendingProposals());
    }

    @PostMapping("/{branchName}/approve")
    public ResponseEntity<?> approveProposal(@PathVariable String branchName) throws IOException, InterruptedException {
        approvalService.applyApprovedProposal(branchName);
        return ResponseEntity.ok(Map.of("message", "Proposal applied to database successfully"));
    }

    @PostMapping("/{branchName}/reject")
    public ResponseEntity<?> rejectProposal(@PathVariable String branchName) {
        approvalService.rejectProposal(branchName);
        return ResponseEntity.ok(Map.of("message", "Proposal rejected"));
    }
}