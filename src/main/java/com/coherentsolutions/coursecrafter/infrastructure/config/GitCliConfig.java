//repo root, Git binary path, PAT token
package com.coherentsolutions.coursecrafter.infrastructure.config;

import com.coherentsolutions.coursecrafter.infrastructure.git.GitCliService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitCliConfig {
    @Value("${git.repo.root}")
    private String repoRoot;          // injected from YAML

    @Value("${git.repo.remote:origin}")
    private String remote;            // defaults to “origin” if not set

    @Value("${git.repo.defaultBranch:main}")
    private String defaultBranch;     // defaults to “main”

    @Value("${git.enabled:true}")
    private boolean enabled;

    @Bean
    public GitCliService gitCliService() {
        return new GitCliService(repoRoot, remote, defaultBranch, enabled);
    }
}
