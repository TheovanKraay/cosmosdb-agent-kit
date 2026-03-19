package com.example.multitenentsaas.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Task entity within a project.
 * Partition key: /tenantId=<id>, /type="task", /projectId=<project-id>
 *
 * Tasks are the most queried entity type. The hierarchical partition key
 * ensures queries within a project hit a single partition.
 *
 * Embedded comments (Rule 1.3) — bounded to recent comments only.
 * Denormalized assignee name (Rule 1.2) for display without extra queries.
 */
public class Task extends BaseEntity {

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status; // "open", "in_progress", "completed", "cancelled"

    @JsonProperty("priority")
    private String priority; // "low", "medium", "high", "critical"

    @JsonProperty("assigneeId")
    private String assigneeId;

    /** Denormalized assignee name (Rule 1.2) */
    @JsonProperty("assigneeName")
    private String assigneeName;

    @JsonProperty("dueDate")
    private Instant dueDate;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    /** Embedded comments (Rule 1.3): bounded list, most recent first */
    @JsonProperty("comments")
    private List<Comment> comments = new ArrayList<>();

    @JsonProperty("tags")
    private List<String> tags = new ArrayList<>();

    public Task() {
        setType("task");
    }

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    /**
     * Embedded comment (Rule 1.3: embed related data retrieved together).
     * Bounded — only store the most recent N comments.
     * Older comments would be stored as separate documents if needed (Rule 1.7).
     */
    public static class Comment {
        @JsonProperty("commentId")
        private String commentId;

        @JsonProperty("authorId")
        private String authorId;

        @JsonProperty("authorName")
        private String authorName;

        @JsonProperty("text")
        private String text;

        @JsonProperty("createdAt")
        private Instant createdAt;

        public String getCommentId() { return commentId; }
        public void setCommentId(String commentId) { this.commentId = commentId; }

        public String getAuthorId() { return authorId; }
        public void setAuthorId(String authorId) { this.authorId = authorId; }

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}
