package digitalnoticeboard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NoticeBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Notice> notices;

    public NoticeBoard() {
        this.notices = new ArrayList<>();
    }

    public Notice addNotice(String title, String category, String content, String postedBy) {
        Notice notice = new Notice(title, category, content, postedBy);
        notices.add(notice);
        return notice;
    }

    public boolean updateNotice(String noticeId, String title, String category, String content, String changedBy) {
        Optional<Notice> notice = findNoticeById(noticeId);
        notice.ifPresent(value -> value.updateNotice(title, category, content, changedBy));
        return notice.isPresent();
    }

    public boolean archiveNotice(String noticeId) {
        Optional<Notice> notice = findNoticeById(noticeId);
        notice.ifPresent(Notice::archive);
        return notice.isPresent();
    }

    public boolean restoreNotice(String noticeId) {
        Optional<Notice> notice = findNoticeById(noticeId);
        notice.ifPresent(Notice::restore);
        return notice.isPresent();
    }

    public Optional<Notice> findNoticeById(String noticeId) {
        return notices.stream()
                .filter(notice -> notice.getId().equals(noticeId))
                .findFirst();
    }

    public List<Notice> getCurrentNotices() {
        return filterByArchiveStatus(false);
    }

    public List<Notice> getPastNotices() {
        return filterByArchiveStatus(true);
    }

    public List<Notice> getAllNotices() {
        return notices.stream()
                .sorted(Comparator.comparing(Notice::getLastUpdatedAt).reversed())
                .collect(Collectors.toList());
    }

    private List<Notice> filterByArchiveStatus(boolean archived) {
        return notices.stream()
                .filter(notice -> notice.isArchived() == archived)
                .sorted(Comparator.comparing(Notice::getLastUpdatedAt).reversed())
                .collect(Collectors.toList());
    }
}

