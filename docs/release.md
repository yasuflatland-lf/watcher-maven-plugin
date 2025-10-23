# Release Guide

This project publishes through [JitPack](https://jitpack.io/), which clones the GitHub repository, runs the build defined in `jitpack.yml`, and serves the artifacts. No manual Maven deployment is required.

## 1. Prepare the Release

- Finish all planned work on `main`
- Update `pom.xml` and documentation with the new version if needed
- Verify a clean build locally:
  ```bash
  mvn clean verify
  ```
- Update release notes (README, CHANGELOG, etc.)

## 2. Create and Push a Tag

Use annotated tags for better metadata in GitHub and JitPack:

```bash
git tag -a v0.9.1 -m "Version 0.9.1 - Feature summary"
git push origin v0.9.1
```

**Note**: Tags must match the version consumers will request from JitPack (e.g., `v0.9.1`).

## 3. GitHub Actions Workflow

The CI workflow (`.github/workflows/ci.yml`) runs on:
- Push to `main` branch
- Pull requests to `main` branch
- GitHub Release creation

**Build job** (`mvn clean verify`):
- Checks out the repository
- Sets up JDK 21
- Runs all tests
- Uploads test results (Surefire XML) - always uploaded
- Uploads build artifacts (JAR, POM) - **only for push to main and release events** (not for pull requests)
- Updates JaCoCo coverage badges/summary via [cicirello/jacoco-badge-generator](https://github.com/cicirello/jacoco-badge-generator) on successful pushes to `main`

**Important**: The CI workflow does **not** publish to Maven repositories. JitPack builds and serves artifacts independently from the GitHub source.

## 4. (Optional) Create a GitHub Release

While not required for JitPack, creating a GitHub Release provides better visibility:

1. Navigate to **Releases** on the GitHub repository
2. Click **"Create a new release"**
3. Select the tag you just pushed (e.g., `v0.9.1`)
4. Fill in the release title and description
5. Click **"Publish release"**

This triggers the CI workflow to upload build artifacts (JAR, POM) to GitHub Actions artifacts section for easy inspection.

## 5. Trigger and Monitor the JitPack Build

Open the JitPack build page for the tag to trigger a build and view logs:

```
https://jitpack.io/#yasuflatland-lf/watcher-maven-plugin/v0.9.1
```

The page shows build status, Maven coordinates, and download links.

## 6. Share the Release

**Consumer setup**:

1. Add the JitPack plugin repository:
   ```xml
   <pluginRepositories>
       <pluginRepository>
           <id>jitpack.io</id>
           <url>https://jitpack.io</url>
       </pluginRepository>
   </pluginRepositories>
   ```

2. Reference the tag or snapshot:
   ```xml
   <plugin>
       <groupId>com.github.yasuflatland-lf</groupId>
       <artifactId>watcher-maven-plugin</artifactId>
       <version>v0.9.1</version>
   </plugin>
   ```

**Note**: No credentials or additional distribution steps are required. If a build fails on JitPack, fix the issue, push a new commit/tag, and re-run the build from the JitPack interface.
