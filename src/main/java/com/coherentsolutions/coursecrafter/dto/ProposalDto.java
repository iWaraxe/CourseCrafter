//{lectureId, slideId, action, text}
package com.coherentsolutions.coursecrafter.dto;

// ------------ Analyzer -> Updater suggestion ------------
public record ProposalDto() {
    public enum Action { ADD, UPDATE, DELETE }
}