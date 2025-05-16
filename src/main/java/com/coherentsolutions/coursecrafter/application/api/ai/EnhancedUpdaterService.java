package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.infrastructure.git.GitContentSyncService;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.infrastructure.git.GitCliService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedUpdaterService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentNodeService nodeService;
    private final GitCliService gitService;
    private final GitContentSyncService gitContentSyncService;

    /**
     * Apply a list of AI-generated proposals to the content structure
     */
    @Transactional
    public List<ContentNode> applyProposals(List<AiProposalDto> proposals)
            throws IOException, InterruptedException {

        List<ContentNode> updatedNodes = new ArrayList<>();
        boolean gitChanges = false;

        // Create a single branch for all changes
        String branchName = "content-update-" + System.currentTimeMillis();
        gitService.createBranch(branchName);

        try {
            // Process all proposals
            for (AiProposalDto proposal : proposals) {
                ContentNode node;

                switch (proposal.action()) {
                    case "ADD":
                        node = createNewNode(proposal);
                        updatedNodes.add(node);

                        // Sync the node to the Git repository - Pass the branch name here
                        gitChanges |= gitContentSyncService.syncNodeToFile(node, branchName);
                        break;

                    case "UPDATE":
                        node = updateExistingNode(proposal);
                        if (node != null) {
                            updatedNodes.add(node);

                            // Sync the node to the Git repository - Pass the branch name here
                            gitChanges |= gitContentSyncService.syncNodeToFile(node, branchName);
                        }
                        break;

                    case "DELETE":
                        deleteNode(proposal.targetNodeId());
                        break;
                }
            }

            // Make a single commit with all changes
            if (!updatedNodes.isEmpty() && gitChanges) {
                gitService.commitAllChanges(
                        "Apply AI content updates: " + updatedNodes.size() + " changes");

                // Push and create PR
                gitService.pushBranch(branchName);
                gitService.createPr(
                        branchName,
                        "Content Updates: " + updatedNodes.size() + " changes",
                        generatePrDescription(proposals, updatedNodes));
            } else if (!updatedNodes.isEmpty()) {
                log.warn("Database nodes were updated but no Git files were changed. PR not created.");
            }

            return updatedNodes;
        } catch (Exception e) {
            log.error("Failed to apply proposals: {}", e.getMessage(), e);
            // Try to clean up the branch if possible
            try {
                gitService.resetToMain();
            } catch (Exception resetEx) {
                log.error("Failed to reset to main: {}", resetEx.getMessage());
            }
            throw e;
        }
    }

    private ContentNode createNewNode(AiProposalDto proposal) throws IOException, InterruptedException {
        // Get parent node with better fallback handling
        ContentNode parent;

        if (proposal.parentNodeId() == 0 || !nodeRepository.existsById(proposal.parentNodeId())) {
            // Find the first course node as a fallback parent
            parent = nodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No course nodes found to use as parent"));

            log.warn("Invalid parent ID {}. Using course node {} as fallback parent.",
                    proposal.parentNodeId(), parent.getId());
        } else {
            parent = nodeRepository.findById(proposal.parentNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent node not found: " + proposal.parentNodeId()));
        }

        // Create the new node
        ContentNode node = ContentNode.builder()
                .parent(parent)
                .nodeType(ContentNode.NodeType.valueOf(proposal.nodeType()))
                .title(proposal.title())
                .nodeNumber(proposal.nodeNumber())
                .displayOrder(proposal.displayOrder() != null ? proposal.displayOrder() : 100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save node and initial version
        return nodeService.createNode(node, proposal.content(),
                "Add new " + proposal.nodeType() + ": " + proposal.title());
    }

    private ContentNode updateExistingNode(AiProposalDto proposal) throws IOException, InterruptedException {
        Optional<ContentNode> existingNode = nodeRepository.findById(proposal.targetNodeId());

        if (existingNode.isPresent()) {
            ContentNode node = existingNode.get();

            // Update basic properties if provided
            if (proposal.title() != null && !proposal.title().isBlank()) {
                node.setTitle(proposal.title());
            }

            if (proposal.nodeNumber() != null && !proposal.nodeNumber().isBlank()) {
                node.setNodeNumber(proposal.nodeNumber());
            }

            if (proposal.displayOrder() != null) {
                node.setDisplayOrder(proposal.displayOrder());
            }

            node.setUpdatedAt(LocalDateTime.now());

            // Create new content version
            return nodeService.updateNode(node.getId(), proposal.content(),
                    "Update " + node.getNodeType() + ": " + node.getTitle());
        }

        return null;
    }

    private void deleteNode(Long nodeId) throws IOException, InterruptedException {
        nodeService.deleteNode(nodeId, "Delete content node: " + nodeId);
    }

    private String generatePrDescription(List<AiProposalDto> proposals, List<ContentNode> updatedNodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI-Generated Content Updates\n\n");
        sb.append("This PR contains the following ").append(proposals.size()).append(" changes:\n\n");

        int i = 1;
        for (AiProposalDto proposal : proposals) {
            sb.append("## ").append(i++).append(". ")
                    .append(proposal.action()).append(": ")
                    .append(proposal.title()).append("\n\n");

            sb.append("**Node Type:** ").append(proposal.nodeType()).append("\n");
            sb.append("**Rationale:** ").append(proposal.rationale()).append("\n\n");

            if (!"DELETE".equals(proposal.action())) {
                // Use HTML details tag to avoid shell interpretation issues
                sb.append("<details>\n<summary>Content Preview</summary>\n\n");
                String contentPreview = proposal.content().length() > 300
                        ? proposal.content().substring(0, 300) + "..."
                        : proposal.content();
                // Use HTML code blocks instead of markdown code blocks
                sb.append("<pre>\n").append(contentPreview).append("\n</pre>\n</details>\n\n");
            }
        }

        sb.append("Please review these changes and provide feedback.");

        return sb.toString();
    }
}