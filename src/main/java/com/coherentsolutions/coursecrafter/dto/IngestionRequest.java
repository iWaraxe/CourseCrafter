// request/response & AI-exchange POJOs
package com.coherentsolutions.coursecrafter.dto;

// ------------ Incoming ingestion payload ------------
public record IngestionRequest(
        String contentType,   // TEXT, URL, YOUTUBE
        String payload        // raw text or URL
) {}
