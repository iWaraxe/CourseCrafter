package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.repo.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.repo.ContentVersionRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.infrastructure.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnhancedUpdaterService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentNodeService nodeService;
    private final GitCliService gitService;

    /**
     * Apply a list of AI-generated proposals to the content structure
     */
    @Transactional
    public List<ContentNode> applyProposals(List<AiProposalDto> proposals)
            throws IOException, InterruptedException {

        List<ContentNode> updatedNodes = new ArrayList<>();
        String branchName = "content-update-" + System.currentTimeMillis();

        for (AiProposalDto proposal : proposals) {
            ContentNode node;

            switch (proposal.action()) {
                case "ADD":
                    node = createNewNode(proposal);
                    updatedNodes.add(node);
                    break;

                case "UPDATE":
                    node = updateExistingNode(proposal);
                    if (node != null) {
                        updatedNodes.add(node);
                    }
                    break;

                case "DELETE":
                    deleteNode(proposal.targetNodeId());
                    break;
            }
        }

        // Commit all changes together
        if (!updatedNodes.isEmpty()) {
            gitService.commitAndPush(
                    branchName,
                    "Apply AI content updates: " + updatedNodes.size() + " changes");

            // Create PR
            gitService.createPr(
                    branchName,
                    "Content Updates: " + updatedNodes.size() + " changes",
                    generatePrDescription(proposals, updatedNodes));
        }

        return updatedNodes;
    }

    private ContentNode createNewNode(AiProposalDto proposal) throws IOException, InterruptedException {
        // Get parent node
        ContentNode parent = nodeRepository.findById(proposal.parentNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Parent node not found: " + proposal.parentNodeId()));

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
                sb.append("<details>\n<summary>Content Preview</summary>\n\n```markdown\n");
                String contentPreview = proposal.content().length() > 300
                        ? proposal.content().substring(0, 300) + "..."
                        : proposal.content();
                sb.append(contentPreview).append("\n```\n</details>\n\n");
            }
        }

        sb.append("Please review these changes and provide feedback.");

        return sb.toString();
    }
}