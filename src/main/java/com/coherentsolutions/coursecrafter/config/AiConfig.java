// ─────────────────────────────────────────────────────────
// CONFIGURATION LAYER  (package com.coursecrafter.config)
// ─────────────────────────────────────────────────────────
//sets API key, chat model, temp, etc.
package com.coherentsolutions.coursecrafter.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.model.ChatModel;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are CourseCrafter AI assistant that helps maintain a Spring Boot course.")
                .build();
    }
}
