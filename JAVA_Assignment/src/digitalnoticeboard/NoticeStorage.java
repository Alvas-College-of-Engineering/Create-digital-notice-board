package digitalnoticeboard;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class NoticeStorage {
    private final Path storagePath;

    public NoticeStorage(Path storagePath) {
        this.storagePath = storagePath;
    }

    public NoticeBoard load() {
        if (!Files.exists(storagePath)) {
            return new NoticeBoard();
        }

        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(storagePath))) {
            Object value = input.readObject();
            if (value instanceof NoticeBoard) {
                return (NoticeBoard) value;
            }
        } catch (IOException | ClassNotFoundException exception) {
            System.err.println("Unable to load notice board data: " + exception.getMessage());
        }

        return new NoticeBoard();
    }

    public void save(NoticeBoard board) throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(storagePath))) {
            output.writeObject(board);
        }
    }
}

