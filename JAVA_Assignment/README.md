# Digital Notice Board

Advance Java assignment project: a local Java web based Digital Notice Board for administrators to create, update, archive, restore, and view notices from a browser.

## Features

- Post new notices with title, category, author, and content.
- Update notice content dynamically from the admin panel.
- Store full notice history for every update.
- Archive current notices and restore past notices.
- View current notices, past notices, and update history.
- Persist all data locally in `data/notices.ser`.

## Project Structure

```text
src/digitalnoticeboard/
  DigitalNoticeBoardApp.java
  Notice.java
  NoticeBoard.java
  NoticeHistoryEntry.java
  NoticeStorage.java
```

## Run Locally

```powershell
javac -d out src\digitalnoticeboard\*.java
java -cp out digitalnoticeboard.DigitalNoticeBoardApp
```

Then open:

```text
http://localhost:8080/
```

To use a different port:

```powershell
java -cp out digitalnoticeboard.DigitalNoticeBoardApp 9090
```
