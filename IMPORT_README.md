## README: Course Content Import

This document explains how to import your course content markdown files into the CourseCrafter application.

### Overview

The import process will:
1. Copy your markdown files to the application's resources directory
2. Parse the content and create a hierarchical structure in the database
3. Extract slide components (scripts, visuals, demonstrations)
4. Generate tags for improved searchability

### Prerequisites

- Your course content must be in markdown (.md) files
- Files should follow the naming pattern: `Lecture X- Title.md` (where X is a number)
- Markdown should be structured with headings using `#`, `##`, `###` symbols
- Sections and subsections should be numbered (e.g., "### 1. Introduction")

### Import Instructions

1. **Prepare Your Files**:
    - Ensure your markdown files are in the expected format
    - Check that heading levels and numbering are consistent

2. **Place Files in Input Directory**:
    - Put your files in one of these locations (checked in this order):
        - `course_content/` (root of project)
        - `src/main/resources/`
        - `src/main/java/com/coherentsolutions/coursecrafter/resources/`
        - `data/course_content/`

3. **Configure Application**:
    - Make sure `application-dev.properties` has:
      ```properties
      coursecrafter.import.enabled=true
      ```

4. **Run the Application**:
    - Start the application with the `dev` profile:
      ```
      ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
      ```
    - Or set the environment variable: `SPRING_PROFILES_ACTIVE=dev`

5. **Verify Import**:
    - Check the logs for success messages
    - Use the API to view the imported content
    - After successful import, disable it to prevent re-importing:
      ```properties
      coursecrafter.import.enabled=false
      ```

### Markdown Structure Guidelines

For best results, format your markdown files like this:

```markdown
## Lecture X: Title

Introduction text here...

### 1. Section Title

Section content...

#### 1.1. Subsection Title

Subsection content...

**Script:** "Speaker notes go here..."

**Slide:** 
- Bullet points
- For visual content

**Demonstration:** Instructions for demos...
```

### Troubleshooting

- **Files not found**: Check the paths and naming conventions
- **Content not properly structured**: Check if your heading levels use the right number of `#` symbols
- **Missing components**: Check if your component markers (Script, Slide, Demo) are properly formatted

### Reset and Reimport

If you need to reset and reimport:

1. Stop the application
2. Clear the database (using provided scripts or database tools)
3. Set `coursecrafter.import.enabled=true`
4. Restart the application

### Contact

For issues or questions, contact the development team.