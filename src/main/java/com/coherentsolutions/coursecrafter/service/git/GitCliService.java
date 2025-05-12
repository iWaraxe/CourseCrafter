// ProcessBuilder wrapper, fall-back to JGitService if needed
package com.coherentsolutions.coursecrafter.service.git;

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

    private void run(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", cmd));
        }
    }
}