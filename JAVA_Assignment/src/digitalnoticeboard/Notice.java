package digitalnoticeboard;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Notice implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final String id;
    private String title;
    private String category;
    private String content;
    private String postedBy;
    private final LocalDateTime postedAt;
    private LocalDateTime lastUpdatedAt;
    private boolean archived;
    private final List<NoticeHistoryEntry> history;

    public Notice(String title, String category, String content, String postedBy) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.category = category;
        this.content = content;
        this.postedBy = postedBy;
        this.postedAt = LocalDateTime.now();
        this.lastUpdatedAt = postedAt;
        this.archived = false;
        this.history = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public String getPostedBy() {
        return postedBy;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public List<NoticeHistoryEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void updateNotice(String newTitle, String newCategory, String newContent, String changedBy) {
        String oldContent = content;
        this.title = newTitle;
        this.category = newCategory;
        this.content = newContent;
        this.lastUpdatedAt = LocalDateTime.now();
        this.history.add(new NoticeHistoryEntry(changedBy, oldContent, newContent));
    }

    public void archive() {
        this.archived = true;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public void restore() {
        this.archived = false;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public String getFormattedPostedAt() {
        return postedAt.format(FORMATTER);
    }

    public String getFormattedLastUpdatedAt() {
        return lastUpdatedAt.format(FORMATTER);
    }

    public String getStatus() {
        return archived ? "Past" : "Current";
    }

    @Override
    public String toString() {
        return title + " (" + category + ")";
    }
}

