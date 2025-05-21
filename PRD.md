# Product Requirements Document: CourseCrafter

**Version:** 1.0
**Date:** May 19, 2025
**Author:** Igor Waraxe
**Status:** Draft

## 1. Introduction

CourseCrafter is a Software-as-a-Service (SaaS) application designed to revolutionize how educators, instructional designers, and content creators update and maintain educational course materials. By leveraging Artificial Intelligence, CourseCrafter automates the summarization of new information, proposes intelligent integrations into existing course structures (stored as Markdown in Git repositories), and facilitates an AI-assisted iterative review process via Pull Requests (PRs). The goal is to significantly reduce manual effort, enhance content relevance, and accelerate the course evolution lifecycle.

## 2. Goals

*   **P0:** Provide a seamless, AI-assisted workflow for updating and maintaining course content.
*   **P0:** Significantly reduce the manual effort required for integrating new information into existing courses.
*   **P1:** Improve the relevance and timeliness of course materials by facilitating quicker updates.
*   **P1:** Offer an intuitive user experience for managing courses, content ingestion, and the AI-driven review process.
*   **P2:** Establish a platform that can be extended with more advanced AI content generation and analytics features in the future.

## 3. Target Users & Use Cases

*   **Primary User Persona 1: University Professor / Lecturer**
    *   **Needs:** Keep their lecture notes and course materials (Markdown files in Git) updated with the latest research, articles, and relevant videos.
    *   **Use Case:** Dr. Smith finds a new research paper and a relevant YouTube lecture. She submits them to CourseCrafter for her "Advanced AI" course. CourseCrafter summarizes them, proposes updates to specific slides in her Markdown files, creates a PR. Dr. Smith reviews, requests a more concise explanation for one section via a PR comment. CourseCrafter revises. Dr. Smith approves and merges. The database updates.
*   **Primary User Persona 2: Corporate Instructional Designer**
    *   **Needs:** Quickly update internal training materials (Markdown in Git) based on new company policies, product updates, or industry best practices.
    *   **Use Case:** ACME Corp releases a new product feature. The instructional designer uploads the internal spec document and a blog post about it. CourseCrafter suggests adding new sections and updating existing examples in their "Product Training" course, creating a PR. The designer and a subject matter expert review, suggest adding a specific disclaimer via PR comments. CourseCrafter revises. They merge.
*   **Primary User Persona 3: Online Course Creator**
    *   **Needs:** Continuously enhance their popular online course (Markdown in Git) with fresh examples, new tools, or emerging concepts.
    *   **Use Case:** A course creator wants to add a new section about a recently released AI tool. They provide a link to the tool's documentation. CourseCrafter drafts the new section, suggests where to place it, and creates a PR. The creator refines the tone through PR comments, merges, and the database reflects the new content.

## 4. Product Features

### 4.1. User & Course Orchestration (MVP)

*   **F-UC-001 (P0): User Account Management:** Users can sign up, log in, and manage their passwords.
*   **F-UC-002 (P0): Course Definition:** Users can define a "Course" entity within the application.
*   **F-UC-003 (P0): Git Repository Linking:** Users can link a Course to an external Git repository (GitHub, GitLab - GitHub MVP).
    *   *Req-UC-003.1:* Securely store Git repository URL and access credentials (e.g., PAT).
*   **F-UC-004 (P0): Initial Course Scan & Import:** Upon linking, the system clones the repository and performs an initial parse of the Markdown structure to populate its internal database (leveraging existing CourseCrafter parsing logic).
*   **F-UC-005 (P1): Job/Task Management (Backend):** System can track the status of content processing jobs (e.g., "ingesting," "AI analyzing," "PR created").

### 4.2. Content Ingestion & Preprocessing (MVP for text, P1 for others)

*   **F-CI-001 (P0): Direct Text Input:** Users can paste raw text for processing.
*   **F-CI-002 (P1): URL Input (Articles, Blog Posts):** Users can submit URLs.
    *   *Req-CI-002.1:* System will attempt to scrape and extract main textual content.
*   **F-CI-003 (P1): YouTube Video Input:** Users can submit YouTube video links.
    *   *Req-CI-003.1:* System will attempt to fetch transcripts.
*   **F-CI-004 (P1): Document Upload (PDF, DOCX):** Users can upload common document formats.
    *   *Req-CI-004.1:* System will attempt to extract text from these documents.
*   **F-CI-005 (P0): Text Cleaning:** Basic cleaning of extracted text (e.g., removing excessive whitespace).

### 4.3. AI Core Services (MVP for core flow, P1 for advanced interpretation)

*   **F-AI-001 (P0): Contextual Summarization:** AI summarizes ingested content, considering the selected target course's existing structure and content as context.
*   **F-AI-002 (P0): Change Proposal Generation:** Based on the summary and course context, AI proposes:
    *   *Req-AI-002.1:* Optimal insertion points for new content sections/slides.
    *   *Req-AI-002.2:* Specific existing content sections/slides to update.
    *   *Req-AI-002.3:* Generates the actual Markdown content for these additions/updates.
*   **F-AI-003 (P1): Iterative Review AI - PR Comment Interpretation:** AI analyzes user comments on the generated PR to understand requested revisions.
    *   *Req-AI-003.1:* The AI will attempt to understand instructions like "make this more concise," "add an example here," "rephrase this paragraph."
*   **F-AI-004 (P1): AI-Driven Revision Generation:** Based on interpreted PR comments, AI generates revised Markdown changes for the relevant sections.
*   **F-AI-005 (P0): Markdown Content Generation:** AI generates well-formatted Markdown that can be directly committed.

### 4.4. Git Workflow Automation (P0 for core, P1 for comment handling)

*   **F-GW-001 (P0): Automated Branch Creation:** System creates a new, uniquely named feature branch in the user's linked Git repository for each set of proposed changes.
*   **F-GW-002 (P0): Automated Commit:** System commits the AI-generated Markdown changes to the feature branch.
*   **F-GW-003 (P0): Automated Pull Request (PR) Creation:** System creates a PR from the feature branch to the course's main/default branch. PR description should summarize proposed changes.
*   **F-GW-004 (P1): PR Comment Fetching:** System can fetch comments from the active PR (via API).
*   **F-GW-005 (P1): Automated Revision Commit:** System commits AI-revised Markdown changes (from F-AI-004) to the *same* feature branch, updating the PR.

### 4.5. Database Synchronization (P0)

*   **F-DS-001 (P0): PR Merge Detection:** System detects when a PR generated by CourseCrafter has been merged in the Git provider (via webhook MVP, or polling as fallback).
*   **F-DS-002 (P0): Post-Merge DB Update:** Upon PR merge detection, the system:
    *   *Req-DS-002.1:* Fetches the updated content from the main branch of the Git repository.
    *   *Req-DS-002.2:* Re-parses the relevant (changed) Markdown files.
    *   *Req-DS-002.3:* Updates its internal database (ContentNodes, SlideComponents, etc.) to reflect the new state of the course.

### 4.6. Frontend Application (Web UI) (MVP for core, P1 for enhancements)

*   **F-FE-001 (P0): User Dashboard:** Displays a list of user's courses and their status.
*   **F-FE-002 (P0): Course Setup & Configuration:** Interface to add a new course and link its Git repository.
*   **F-FE-003 (P0): Content Submission Form:** Interface for users to select a course and submit new content (text, URL, etc.).
*   **F-FE-004 (P0): PR Monitoring View:** Displays active PRs generated by CourseCrafter, their status (e.g., "Awaiting Review," "AI Revising"), and a link to the PR on the Git provider.
*   **F-FE-005 (P1): "AI Revise" Trigger:** A button/action in the PR Monitoring View that prompts the system to fetch PR comments and trigger AI revision.
*   **F-FE-006 (P1): User Notifications:** In-app notifications for key events (e.g., "PR Created," "AI Revision Complete," "DB Sync Complete").

## 5. Design & UX Considerations

*   **Simplicity:** The workflow, while complex internally, must be presented to the user in a simple, step-by-step manner.
*   **Clarity:** Users must clearly understand what the AI is proposing and why. PR descriptions should be informative.
*   **Control:** Users must always have the final say (by merging the PR). The AI assists, it doesn't dictate.
*   **Feedback Loop:** The iterative review process should be easy to engage with.
*   **Transparency:** Provide insight into what the AI is doing (e.g., "Summarizing content," "Analyzing PR comments").

## 6. Technical Requirements & Assumptions

*   **TR-001:** Application will be built using Java 21, Spring Boot 3.4.x, and PostgreSQL.
*   **TR-002:** OpenAI GPT models (or a compatible alternative) will be used for AI tasks via Spring AI.
*   **TR-003:** Git CLI and GitHub CLI (`gh`) must be available on the application server environment for Git operations. (Alternative: Use native Java Git libraries like JGit and GitHub/GitLab REST API clients).
*   **TR-004:** Users are expected to have their course content in Markdown format.
*   **TR-005:** Users will manage their Git repositories on GitHub or GitLab (GitHub for MVP).
*   **TR-006:** The system assumes a reasonably standard Markdown structure that the existing CourseCrafter parser can handle (H1 for Course, H2 for Lecture, etc.).
*   **Assumption-001:** Users are comfortable with the Git Pull Request workflow for reviewing changes.
*   **Assumption-002:** LLM APIs are capable of generating coherent Markdown and interpreting review comments with sufficient accuracy for this workflow.

## 7. Release Criteria (for V1 MVP)

*   All P0 features are implemented and tested.
*   Users can successfully onboard, link a GitHub repository, submit text content, and receive a PR with AI-proposed changes.
*   The system can detect a PR merge (manual check/polling acceptable for MVP if webhooks are complex) and update its database.
*   The core review loop (user comments -> AI interpretation -> AI revision -> commit to branch) is functional for simple revisions (P1 feature, but a basic version is desirable for MVP's core value).
*   Basic security measures for user accounts and Git credentials are in place.
*   Application is deployable to a cloud environment.

## 8. Success Metrics

*   **Activation:** Number of users successfully linking a course repository.
*   **Engagement:** Number of content update jobs initiated per user.
*   **AI Effectiveness:**
    *   Percentage of AI-generated PRs merged by users.
    *   Average number of AI revision cycles per PR before merge.
    *   User satisfaction survey on the quality of AI proposals.
*   **Efficiency Gain:** (Qualitative initially) Feedback on time saved compared to manual updates.
*   **System Stability:** Uptime, error rates in AI processing and Git operations.

## 9. Future Considerations (Post-V1)

*   Support for more Git providers (Bitbucket, self-hosted).
*   Direct in-app Markdown editing and diff viewing.
*   Advanced analytics on content (e.g., identifying outdated sections).
*   AI-powered content generation (e.g., "draft a new slide on topic X").
*   Deeper integration with Learning Management Systems (LMS).
*   Subscription tiers and billing.
*   Vector database integration for more nuanced semantic understanding of course content and submitted materials.
*   Support for other content formats (e.g., audio files, images).
*   Customizable AI personas or instruction sets per course.

## 10. Open Questions

*   How to best handle merge conflicts if the user modifies the feature branch outside of CourseCrafterCourseCrafter? (Initial stance: User's responsibility, or disallow external modifications to AI branch).
*   What is the optimal level of detail for PR descriptions generated by the AI? Description should have a small summary of suggested changes.
*   How to manage costs associated with LLM API calls, especially during iterative revisions? (e.g., limit revision cycles, use cheaper models for some tasks).
*   Specific strategies for prompting the AI to interpret PR comments accurately.
*   Detailed error handling and recovery strategies for each step of the complex workflow.
*   How to manage different versions of the same course if the user has multiple active PRs/branches? (V1 likely supports one active update job/PR per course at a time).