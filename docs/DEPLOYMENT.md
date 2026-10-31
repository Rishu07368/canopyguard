# Windows deployment

## Build machine

Use Windows, PowerShell, Maven 3.8+, and a full JDK 17+ containing `jpackage`. Confirm `java -version`, `mvn -version`, and `jpackage --version`.

## Build and package

From the project directory:

```powershell
.\scripts\package-windows.ps1
```

The script runs JUnit tests, builds the shaded application JAR, packages it as a Windows app image, zips the image, and checks that the launcher exists. The generated runtime is included in the app image. Distribute the complete ZIP, not the executable alone.

Run the app image on the build computer before distributing. The prototype's user data is stored outside the app image at `%APPDATA%\CanopyGuard\data`, so upgrading the app does not replace saved checkpoints or the local database.

## Limitations

The packaging workflow is intended for Windows-on-Windows builds and does not sign the app. A successful Maven test is not a substitute for testing the packaged launcher and extracted ZIP on a Windows machine without a JDK on `PATH`.
