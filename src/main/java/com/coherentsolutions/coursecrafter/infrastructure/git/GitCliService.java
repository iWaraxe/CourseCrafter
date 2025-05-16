// ProcessBuilder wrapper, fall-back to JGitService if needed
package com.coherentsolutions.coursecrafter.infrastructure.git;

import groovyjarjarpicocli.CommandLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    /**
     * Create a new branch from main
     */
    public void createBranch(String branch) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping branch creation");
            return;
        }

        run("git", "-C", repoRoot, "checkout", "main");
        run("git", "-C", repoRoot, "pull", "origin", "main");
        run("git", "-C", repoRoot, "checkout", "-B", branch);
    }

    /**
     * Commit all changes in the working directory
     */
    public void commitAllChanges(String message) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping commit");
            return;
        }

        // Add all changes
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

        // Commit with the message
        run("git", "-C", repoRoot, "commit", "-m", message);
    }

    /**
     * Push the current branch to the remote
     */
    public void pushBranch(String branch) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping push");
            return;
        }

        run("git", "-C", repoRoot, "push", "-f", remote, branch);
    }

    /**
     * Reset to main branch and clean the working directory
     */
    public void resetToMain() throws IOException, InterruptedException {
        if (!enabled) {
            return;
        }

        run("git", "-C", repoRoot, "reset", "--hard", "HEAD");
        run("git", "-C", repoRoot, "clean", "-fd");
        run("git", "-C", repoRoot, "checkout", "main");
    }

    public void commitAndPush(String branch, String message) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping commit and push");
            return; // Skip Git operations if disabled
        }

        // Check if repo path exists
        Path repoPath = Paths.get(repoRoot);
        if (!Files.exists(repoPath)) {
            log.error("Git repository path does not exist: {}", repoRoot);
            log.info("Creating directory structure...");
            try {
                Files.createDirectories(repoPath);
                // Initialize git repo if needed
                ProcessBuilder initPb = new ProcessBuilder("git", "init", repoRoot);
                Process initProcess = initPb.start();
                if (initProcess.waitFor() != 0) {
                    log.error("Failed to initialize git repository");
                    return;
                }
            } catch (IOException e) {
                log.error("Failed to create repository directory: {}", e.getMessage());
                return;
            }
        }

        try {
            // First check if git is available
            ProcessBuilder checkGit = new ProcessBuilder("git", "--version");
            Process gitCheck = checkGit.start();
            int gitResult = gitCheck.waitFor();
            if (gitResult != 0) {
                log.error("Git command not available. Please ensure git is installed and in the PATH.");
                return;
            }

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
            // Don't rethrow - log the error but don't fail the whole operation
            // This allows the database changes to persist even if Git fails
        }
    }

    /**
     * Opens a GitHub Pull Request for the given branch.
     * Requires the GitHub CLI (`gh`) to be installed and authenticated.
     *
     * @param branch feature branch that was already pushed
     * @param title  PR title
     * @param body   PR body/description
     * @return String PR URL or error message
     */
    public String createPr(String branch, String title, String body) throws IOException, InterruptedException {
        if (!enabled) {
            log.info("Git operations disabled, skipping PR creation");
            return "Git operations disabled";
        }

        try {
            // Check if gh CLI is available
            ProcessBuilder checkGh = new ProcessBuilder("/opt/homebrew/bin/gh", "--version");
            Process ghCheck = checkGh.start();
            int ghResult = ghCheck.waitFor();
            if (ghResult != 0) {
                log.error("GitHub CLI not available. Please ensure gh is installed.");
                return "GitHub CLI not available";
            }

            // Write the PR body to a temporary file to avoid shell interpretation issues
            Path tempFile = Files.createTempFile("pr-description", ".md");
            Files.writeString(tempFile, body);

            // Create PR using the file for the body and capture output to get the PR URL
            String cmd = String.format(
                    "cd %s && /opt/homebrew/bin/gh pr create --title \"%s\" --body-file \"%s\" --base %s --head %s",
                    repoRoot,
                    title.replace("\"", "\\\""),
                    tempFile.toAbsolutePath(),
                    defaultBranch,
                    branch
            );

            // Capture the output of the command to get the PR URL
            ProcessBuilder prCreateProcess = new ProcessBuilder("sh", "-c", cmd);
            prCreateProcess.redirectErrorStream(true);
            Process process = prCreateProcess.start();

            // Read the output which should contain the PR URL
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();

            // Delete the temporary file
            Files.deleteIfExists(tempFile);

            // Check if the PR creation was successful
            if (process.exitValue() == 0 && output.contains("http")) {
                // Extract the PR URL from the output - it's usually the last line
                String[] lines = output.split("\\n");
                for (String line : lines) {
                    if (line.startsWith("http") || line.contains("github.com")) {
                        return line.trim();
                    }
                }

                // If we can't find a URL in the output, construct one based on the repo
                // This is a fallback that may not always work
                String repoName = repoRoot.substring(repoRoot.lastIndexOf('/') + 1);
                String orgName = repoRoot.substring(0, repoRoot.lastIndexOf('/')).substring(repoRoot.substring(0, repoRoot.lastIndexOf('/')).lastIndexOf('/') + 1);
                return String.format("https://github.com/%s/%s/pull/new/%s", orgName, repoName, branch);
            } else {
                log.error("Failed to create PR: {}", output);
                return "PR creation failed: " + output;
            }
        } catch (Exception e) {
            log.error("Failed to create PR: {}", e.getMessage());
            return "Error creating PR: " + e.getMessage();
        }
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