// ProcessBuilder wrapper, fall-back to JGitService if needed
package com.coherentsolutions.coursecrafter.infrastructure.git;

import groovyjarjarpicocli.CommandLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class GitCliService {

    private final String repoRoot;
    private final String remote;
    private final String defaultBranch;
    private final boolean enabled;

    public GitCliService(
            @Value("${git.repo.root}") String repoRoot,
            @Value("${git.repo.remote:origin}") String remote,
            @Value("${git.repo.defaultBranch:main}") String defaultBranch,
            @Value("${git.enabled:true}") boolean enabled) {
        this.repoRoot = repoRoot;
        this.remote = remote;
        this.defaultBranch = defaultBranch;
        this.enabled = enabled;
    }

    public void commitAndPush(String branch, String message) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping commit and push");
            return; // Skip Git operations if disabled
        }

        try {
            run("git", "-C", repoRoot, "checkout", "-B", branch, defaultBranch);
            run("git", "-C", repoRoot, "add", ".");

            // Check if there are changes to commit
            ProcessBuilder pb = new ProcessBuilder("git", "-C", repoRoot, "status", "--porcelain");
            Process p = pb.start();
            String changes = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();

            if (changes.isEmpty()) {
                log.info("No changes to commit, skipping commit operation");
                return;
            }

            run("git", "-C", repoRoot, "commit", "-m", message);
            run("git", "-C", repoRoot, "push", "-f", remote, branch);
        } catch (Exception e) {
            log.error("Git operation failed: {}", e.getMessage());
            // Either re-throw or handle more gracefully
            throw e;
        }
    }

    /**
     * Opens a GitHub Pull Request for the given branch.
     * Requires the GitHub CLI (`gh`) to be installed and authenticated.
     *
     * @param branch feature branch that was already pushed
     * @param title  PR title
     * @param body   PR body/description
     */
    public void createPr(String branch, String title, String body) throws IOException, InterruptedException {
        // We have to run inside the repo root; easiest is to wrap the command in `sh -c "cd … && gh …"`
        String cmd = String.format(
                "cd %s && gh pr create --title \"%s\" --body \"%s\" --base %s --head %s",
                repoRoot,
                title.replace("\"", "\\\""),
                body.replace("\"", "\\\""),
                defaultBranch,
                branch
        );
        run("sh", "-c", cmd);
    }

    private void run(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", cmd));
        }
    }
}