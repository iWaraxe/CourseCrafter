# CourseCrafter SaaS Enhancement - To-Do List

**Legend:**
*   [ ] Task To Do
*   [x] Task Completed (or significantly addressed by existing codebase)
*   `(Ref: <module_or_file>)` Reference to existing relevant module/file if applicable.

## Phase 0: Project Setup & Foundation Review

*   [x] Initial Spring Boot Project Setup (`pom.xml`, `CourseCrafterApplication.java`)
*   [x] Define Core Domain Models (`ContentNode`, `SlideComponent`, `Tag`)
*   [x] Setup Database (PostgreSQL) and JPA (`application.yaml`, entities)
*   [x] Implement Basic AI Integration with Spring AI (`AiConfig.java`, OpenAI)
*   [x/ ] Implement Basic Git CLI Interaction (`GitCliService.java` - needs enhancement for PR comments, webhooks)
*   [x] Implement Markdown Parsing for Initial Import (`MarkdownCourseParser.java`, `DatabasePopulationScript.java`)
*   [ ] **New:** Set up a dedicated project board (Jira, Trello, GitHub Projects) for tracking SaaS features.
*   [ ] **New:** Define clear API contracts (e.g., OpenAPI/Swagger) for new frontend-backend interactions.
*   [ ] **New:** Establish a basic CI/CD pipeline.

## Phase 1: SaaS Foundation & User/Course Management (Backend)

*   **1.1. User Authentication & Authorization:**
    *   [ ] Implement Spring Security for user registration, login, and session management.
    *   [ ] Define User entity and repository.
    *   [ ] Implement API endpoints for user authentication.
*   **1.2. Course Entity Management:**
    *   [ ] Define `Course` entity (associating with a User, Git repository URL, credentials).
    *   [ ] Implement `CourseRepository` and `CourseService`.
    *   [ ] API endpoints for users to CRUD their Courses.
    *   [ ] Securely store Git PATs/credentials (e.g., using Spring Vault or encrypted DB field).
*   **1.3. Initial Course Import & Linking:**
    *   [ ] Adapt existing `MarkdownCourseParser` to run on-demand for a specific user's course when a Git repo is linked.
        *   Ensure multi-tenancy: parsed content should be associated with the user/course.
    *   [ ] Enhance `GitCliService` to clone/pull a *specific user's repository* based on stored credentials.
*   **1.4. Basic Task/Job Management (Backend Stub):**
    *   [ ] Define a simple `UpdateJob` entity to track the state of a content update process (e.g., `PENDING_INGESTION`, `AI_PROCESSING`, `PR_CREATED`, `AWAITING_REVIEW`, `COMPLETED`).
    *   [ ] Implement `UpdateJobRepository` and `UpdateJobService`.

## Phase 2: Content Ingestion & Preprocessing (Backend)

*   **2.1. Direct Text Input:**
    *   [x] Existing capabilities for handling raw text can be adapted. (`EnhancedTextIngestionService`)
*   **2.2. URL Input (Articles, Blog Posts):**
    *   [ ] Implement URL content scraping service (e.g., using Jsoup).
    *   [ ] Integrate scraper into the ingestion workflow.
*   **2.3. YouTube Video Input:**
    *   [ ] Research and integrate a YouTube transcript service/library (e.g., `youtube-dl` + OpenAI Whisper API or Java library).
    *   [ ] Integrate into ingestion workflow.
*   **2.4. Document Upload (PDF/DOCX):**
    *   [ ] Implement document text extraction service (e.g., Apache Tika, PDFBox, POI).
    *   [ ] Add API endpoint for file uploads.
    *   [ ] Integrate into ingestion workflow.
*   **2.5. Unified Ingestion Service:**
    *   [ ] Refactor/Create a service to handle different input types and route them to appropriate extractors.

## Phase 3: AI Core - Summarization & Initial Proposal (Backend)

*   **3.1. Contextual Summarization Service Enhancement:**
    *   [x] Existing `SummarizationService` can summarize.
    *   [ ] Enhance to robustly use the *full current course structure* (from DB for the specific user's course) as context for the LLM. (`ContentHierarchyService.generateDetailedOutlineContext`)
*   **3.2. AI Change Proposal Generation Enhancement:**
    *   [x] Existing `EnhancedAnalyzerService` generates `AiProposalDto`.
    *   [ ] Enhance LLM prompts to be more precise about *why* a change is proposed and *where* it fits.
    *   [ ] Improve logic for determining `targetNodeId` vs. `parentNodeId` accurately.
    *   [ ] Ensure generated Markdown is valid and respects formatting.
*   **3.3. Markdown Modification Strategy:**
    *   [ ] **Critical:** Evaluate and improve how AI-proposed Markdown changes are applied to existing files by `GitContentSyncService.insertContentAtAppropriateLocation`. Current regex-based approach may be insufficient.
        *   Consider using a Markdown AST library (like Flexmark, already a dependency) to parse, modify, and then render Markdown. This is much safer for complex changes.
        *   Alternatively, focus AI on generating *full new sections/slides* or *replacing entire existing sections/slides* to simplify diffing, rather than fine-grained inline edits initially.
*   **3.4. Git Workflow - Initial PR Creation:**
    *   [x] Existing `GitCliService` can create branches, commit. (`EnhancedUpdaterService.createProposalPR`)
    *   [x] Existing `GitCliService` can create PRs using `gh` CLI.
    *   [ ] Ensure PR descriptions clearly summarize AI proposals.
    *   [ ] Link the `UpdateJob` with the created PR URL.

## Phase 4: Iterative AI Review Cycle (Backend & AI)

*   **4.1. PR Comment Fetching:**
    *   [ ] Enhance `GitCliService` (or use GitHub/GitLab API client) to fetch comments from a specific PR associated with an `UpdateJob`.
*   **4.2. PR Comment Interpretation Service (`PrCommentInterpretationService`):**
    *   [ ] Design LLM prompts to take:
        *   The original AI-proposed Markdown change (diff or full content).
        *   User comments from the PR.
        *   Instruction to generate a *revised* Markdown change.
    *   [ ] Develop service to orchestrate this LLM call.
*   **4.3. Revised Change Application:**
    *   [ ] Service to take the AI's revised Markdown.
    *   [ ] Update the corresponding file(s) in the *same feature branch*.
    *   [ ] Commit the changes to the feature branch (this will automatically update the PR).
*   **4.4. UpdateJob Status Update:**
    *   [ ] Reflect "AI_REVISING", "AWAITING_FURTHER_REVIEW" states.

## Phase 5: Database Synchronization Post-Merge (Backend)

*   **5.1. PR Merge Detection:**
    *   [ ] Implement GitHub/GitLab webhook listener endpoint to receive PR merge events.
    *   [ ] (Fallback/Alternative) Implement a polling mechanism for `UpdateJob`s with "PR_CREATED" status to check PR merge status via API.
*   **5.2. Post-Merge Database Update Service:**
    *   [ ] Triggered by merge detection.
    *   [ ] `GitCliService` pulls the updated main branch for the course.
    *   [ ] Re-run `MarkdownCourseParser` (or a refined version) on the changed files in the main branch.
        *   Ensure this updates existing `ContentNode`s and `SlideComponent`s correctly rather than always creating new ones. This might require more sophisticated diffing or matching logic within the parser or a pre-parsing step.
        *   Consider how to handle deletions or major restructuring.
    *   [ ] Update the `UpdateJob` status to "COMPLETED".

## Phase 6: Frontend Application (Web UI)

*   **6.1. Basic Framework & Authentication Views:**
    *   [ ] Choose a frontend framework (React, Vue, Angular, or Thymeleaf).
    *   [ ] Implement Login, Registration, Password Reset pages.
*   **6.2. Dashboard & Course Management Views:**
    *   [ ] View to list user's courses.
    *   [ ] Form to add/edit a course (link Git repo, enter credentials).
    *   [ ] Display initial course structure (read-only from DB).
*   **6.3. Content Submission View:**
    *   [ ] Form to select a target course.
    *   [ ] Input fields for different content types (text area, URL input, file upload).
    *   [ ] Submit button to trigger backend ingestion and AI processing.
*   **6.4. PR Monitoring & Review View:**
    *   [ ] List active `UpdateJob`s / PRs generated by the system.
    *   [ ] Display PR status, link to PR on Git provider.
    *   [ ] Button to "Trigger AI Revision" (calls backend to fetch comments and start AI revision).
*   **6.5. User Notifications (Basic):**
    *   [ ] Simple in-app messages for job status changes.

## Phase 7: Testing, Deployment & Iteration

*   **7.1. Unit & Integration Testing:**
    *   [x] Basic contextLoads test exists. (`CourseCrafterApplicationTests`)
    *   [ ] Write unit tests for new services (Content Ingestion, AI Services, Git Interaction enhancements).
    *   [ ] Write integration tests for key workflows (e.g., text submission to PR creation).
*   **7.2. End-to-End Testing:**
    *   [ ] Manually test the full user flow with a sample Git repository.
    *   [ ] Test the iterative review cycle.
*   **7.3. Deployment:**
    *   [ ] Dockerize the application.
    *   [ ] Choose a cloud provider (e.g., AWS, Google Cloud, Azure, Fly.io, Heroku) and deploy.
*   **7.4. Logging & Monitoring:**
    *   [ ] Ensure comprehensive logging for all critical operations (as already started).
    *   [ ] Set up basic application monitoring.
*   **7.5. Documentation:**
    *   [x] README.md (exists, needs updating for SaaS features).
    *   [x] HELP.md (exists).
    *   [ ] **New:** API documentation for frontend.
    *   [ ] **New:** User guide for SaaS features.
*   **7.6. Security Review:**
    *   [ ] Review handling of Git credentials.
    *   [ ] Review authentication/authorization.
    *   [ ] Protect against prompt injection in AI services.

## Post-MVP / Future Enhancements (Backlog)

*   [ ] Advanced analytics dashboard.
*   [ ] Support for more Git providers.
*   [ ] Real-time collaboration features.
*   [ ] Subscription management and billing.
*   [ ] Deeper Vector DB integration for semantic search / content matching.
*   [ ] Customizable AI personas/instructions per course.
*   [ ] In-app diff viewer for proposed changes.
*   [ ] More granular re-parsing on DB sync (only changed files).
