package digitalnoticeboard;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NoticeHistoryEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final LocalDateTime changedAt;
    private final String changedBy;
    private final String oldContent;
    private final String newContent;

    public NoticeHistoryEntry(String changedBy, String oldContent, String newContent) {
        this.changedAt = LocalDateTime.now();
        this.changedBy = changedBy;
        this.oldContent = oldContent;
        this.newContent = newContent;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getOldContent() {
        return oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public String getFormattedChangedAt() {
        return changedAt.format(FORMATTER);
    }

    @Override
    public String toString() {
        return getFormattedChangedAt() + " by " + changedBy;
    }
}

