package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import com.coherentsolutions.coursecrafter.infrastructure.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentNodeService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;
    private final GitCliService gitService;

    @Transactional
    public ContentNode createNode(ContentNode node, String content, String commitMessage) throws IOException, InterruptedException {
        // Set creation timestamp
        if (node.getCreatedAt() == null) {
            node.setCreatedAt(LocalDateTime.now());
        }
        node.setUpdatedAt(LocalDateTime.now());

        // Generate path if parent exists
        if (node.getParent() != null) {
            ContentNode parent = nodeRepository.findById(node.getParent().getId()).orElseThrow();
            String parentPath = parent.getPath() != null ? parent.getPath() : parent.getNodeType() + "/" + parent.getId();
            node.setPath(parentPath + "/" + node.getNodeType() + "-" + UUID.randomUUID().toString().substring(0, 8));
        } else {
            node.setPath(node.getNodeType() + "/" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Save the node first
        ContentNode savedNode = nodeRepository.save(node);

        // Create initial version
        if (content != null && !content.isBlank()) {
            ContentVersion version = ContentVersion.builder()
                    .node(savedNode)
                    .content(content)
                    .contentFormat("MARKDOWN")
                    .versionNumber(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            versionRepository.save(version);
        }

        // Commit to Git
        String branchName = "update-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);

        return savedNode;
    }

    @Transactional
    public ContentNode updateNode(Long nodeId, String newContent, String commitMessage) throws IOException, InterruptedException {
        ContentNode node = nodeRepository.findById(nodeId).orElseThrow();
        node.setUpdatedAt(LocalDateTime.now());

        // Get current version number
        int currentVersion = versionRepository.findLatestVersionByNodeId(nodeId)
                .map(ContentVersion::getVersionNumber)
                .orElse(0);

        // Create new version
        ContentVersion version = ContentVersion.builder()
                .node(node)
                .content(newContent)
                .contentFormat("MARKDOWN")
                .versionNumber(currentVersion + 1)
                .createdAt(LocalDateTime.now())
                .build();
        versionRepository.save(version);

        // Update node in repository
        ContentNode updatedNode = nodeRepository.save(node);

        // Commit to Git
        String branchName = "update-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);

        return updatedNode;
    }

    public Optional<ContentNode> getNodeWithLatestContent(Long nodeId) {
        Optional<ContentNode> nodeOpt = nodeRepository.findById(nodeId);
        if (nodeOpt.isEmpty()) {
            return Optional.empty();
        }

        ContentNode node = nodeOpt.get();
        versionRepository.findLatestVersionByNodeId(nodeId)
                .ifPresent(version -> {
                    // Assuming you have a transient field to hold the content
                    // or you can use a DTO for this purpose
                    node.setMetadataJson("{\"latestContent\": \"" + version.getContent() + "\"}");
                });

        return Optional.of(node);
    }

    @Transactional
    public void deleteNode(Long nodeId, String commitMessage) throws IOException, InterruptedException {
        nodeRepository.deleteById(nodeId);

        // Commit to Git
        String branchName = "delete-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);
    }
}