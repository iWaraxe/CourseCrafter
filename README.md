# CourseCrafter

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M8-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)

CourseCrafter is an AI-enhanced course content management system built with Spring Boot. It automates the creation, organization, and improvement of educational materials through AI analysis and structured content management.

## 📋 Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [AI Integration](#ai-integration)
- [Content Structure](#content-structure)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Contributors](#contributors)
- [License](#license)

## 🔍 Overview

CourseCrafter solves several challenges in educational content management:

- **Content Organization**: Automatically structures course content into a logical hierarchy
- **AI Enhancement**: Uses LLMs to improve, analyze, and suggest content changes
- **Version Control**: Tracks all changes to course materials with Git integration
- **Collaborative Workflow**: Enables team editing through PR-based review systems
- **Markdown Import**: Easily imports existing course materials from Markdown files

The system is designed for course creators, educational institutions, and training departments that need to maintain and improve high-quality educational materials at scale.

## ✨ Key Features

### AI-Powered Content Enhancement
- Analyzes and classifies raw content for optimal placement within courses
- Generates improvement proposals for existing content
- Summarizes and cleans instructor-provided materials
- Extracts relevant tags for improved searchability

### Hierarchical Content Organization
- Organizes content into Courses → Lectures → Sections → Topics → Slides
- Further divides slides into component types (Scripts, Visuals, Notes, Demonstrations)
- Preserves logical structure and relationship between content elements

### Version Control and Collaboration
- Integrates with Git for content change tracking
- Automatically generates commits and PRs for content updates
- Maintains full version history of all content changes

### Content Ingestion and Import
- Bulk imports course content from Markdown files
- Automatically parses structure using pattern matching
- Extracts components and creates appropriate database entries

## 🏗️ Architecture

CourseCrafter follows a clean, domain-driven design with distinct layers:

### Domain Layer
- Core business entities (`ContentNode`, `SlideComponent`, `Tag`, etc.)
- Repository interfaces for data access
- Domain services for business logic

### Application Layer
- Coordinates between domain services and external systems
- Handles complex workflows involving multiple domains
- Provides use case implementations for API endpoints

### Infrastructure Layer
- Implements integration with external systems (AI APIs, Git)
- Provides technical services like database access
- Manages configuration and environment setup

### Presentation Layer
- RESTful API controllers for client interaction
- DTOs for request/response data modeling
- Input validation and response formatting

## 💻 Technology Stack

- **Java 21**: Core programming language
- **Spring Boot 3.4.5**: Application framework
- **Spring AI 1.0.0-M8**: Integration with LLMs (currently OpenAI)
- **Spring Data JPA**: ORM and repository abstractions
- **PostgreSQL**: Relational database with PGVector extension
- **Maven**: Build tool and dependency management
- **Lombok**: Boilerplate code reduction
- **Git**: Version control integration
- **Flexmark**: Markdown parsing and processing

## 🧠 AI Integration

CourseCrafter leverages Spring AI to integrate with large language models:

- **Content Analysis**: Examines new content to determine optimal placement within the course structure
- **Proposal Generation**: Creates suggested content improvements with rationales
- **Content Summarization**: Cleans and restructures raw uploads from instructors
- **Tag Extraction**: Identifies relevant keywords and concepts for improved searchability

Currently, the system uses OpenAI's GPT models but is designed to be model-agnostic.

## 📚 Content Structure

Course content follows this hierarchical structure:

```
Course
├── Lecture 1
│   ├── Section 1.1
│   │   ├── Topic 1.1.1
│   │   │   ├── Slide [seq:010]
│   │   │   │   ├── SCRIPT
│   │   │   │   ├── VISUAL
│   │   │   │   ├── NOTES
│   │   │   │   └── DEMONSTRATION
│   │   │   └── Slide [seq:020]
│   │   └── Topic 1.1.2
│   └── Section 1.2
└── Lecture 2
```

Each slide contains up to four component types:
- **SCRIPT**: Instructor speaking notes or explanatory text
- **VISUAL**: Description of images, diagrams, or other visual elements
- **NOTES**: Additional context, resources, or reference material
- **DEMONSTRATION**: Interactive examples or activities

## 🚀 Getting Started

### Prerequisites
- Java 21 or higher
- PostgreSQL with PGVector extension
- Git installed and configured
- OpenAI API key

### Configuration
1. Clone the repository
```bash
git clone https://github.com/yourusername/coursecrafter.git
cd coursecrafter
```

2. Configure application properties
```bash
# Create application-dev.yaml or edit existing one
cp src/main/resources/application.yaml src/main/resources/application-dev.yaml
```

3. Set required environment variables
```bash
export OPENAI_API_KEY=your_openai_api_key
export GITHUB_PAT=your_github_personal_access_token
```

### Building and Running
```bash
# Build the project
./mvnw clean package

# Run with development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Initial Content Import
To import course content from Markdown files:

1. Place your Markdown files in the `course_content` directory
2. Enable content import in `application-dev.yaml`:
```yaml
coursecrafter:
  import:
    enabled: true
    folder: course_content
```
3. Restart the application

## 🌐 API Endpoints

### Content Management
- `GET /api/content/tree` - Get the complete content hierarchy
- `GET /api/content/{nodeId}` - Get a specific content node
- `POST /api/content` - Create a new content node
- `PUT /api/content/{nodeId}` - Update an existing node
- `DELETE /api/content/{nodeId}` - Delete a node

### AI Integration
- `POST /api/ingest/content` - Process new content with AI analysis
- `POST /api/course/{courseName}/update` - Update course with new content

### Slide Management
- `GET /api/slides/{slideId}/components` - Get all components for a slide
- `POST /api/slides/{slideId}/components` - Create a new slide component

## 📄 License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.