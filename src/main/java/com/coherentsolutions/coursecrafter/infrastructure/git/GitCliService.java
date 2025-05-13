// ProcessBuilder wrapper, fall-back to JGitService if needed
package com.coherentsolutions.coursecrafter.infrastructure.git;

import java.io.IOException;

public class GitCliService {

    private final String repoRoot;
    private final String remote;
    private final String defaultBranch;

    public GitCliService(String repoRoot, String remote, String defaultBranch) {
        this.repoRoot = repoRoot;
        this.remote = remote;
        this.defaultBranch = defaultBranch;
    }

    public void commitAndPush(String branch, String message) throws IOException, InterruptedException {
        run("git", "-C", repoRoot, "checkout", "-B", branch, defaultBranch);
        run("git", "-C", repoRoot, "add", ".");
        run("git", "-C", repoRoot, "commit", "-m", message);
        run("git", "-C", repoRoot, "push", "-f", remote, branch);
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