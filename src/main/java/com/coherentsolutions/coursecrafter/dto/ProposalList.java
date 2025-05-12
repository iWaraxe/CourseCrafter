package com.coherentsolutions.coursecrafter.dto;

import java.util.List;

/** Wrapper so the LLM can return a *single* JSON object */
public record ProposalList(List<ProposalDto> proposals) {}