# ChatApp — JavaFX Client

This workspace contains the original ChatApp server and a new JavaFX client `ClientFX`.

Run requirements:
- JDK 17+ installed
- Gradle installed (or use a local Gradle wrapper)

Run the JavaFX client using Gradle:

Windows (PowerShell / CMD):
```bash
gradle run
```

Or if you have `gradlew` wrapper available:
```bash
.\gradlew.bat run
```

Client defaults:
- Host: localhost
- Port: 5000

The client implements a modern JavaFX UI and speaks the same protocol as the included server in `ChatApp.java`.
