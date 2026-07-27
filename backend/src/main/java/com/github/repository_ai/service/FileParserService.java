package com.github.repository_ai.service;

import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.entity.RepositoryFile;
import com.github.repository_ai.repository.RepositoryFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Walks a cloned repository directory tree, reads source/config files,
 * and persists each as a {@link RepositoryFile} row in the database.
 * <p>
 * Safety features:
 * - Only reads files with known text extensions (never touches binaries)
 * - Skips noise directories (.git, node_modules, target, build, dist, etc.)
 * - Skips oversized files (likely generated/minified)
 */
@Service
public class FileParserService {

    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);

    private final RepositoryFileRepository repositoryFileRepository;

    @Value("${app.parser.max-file-size-bytes:512000}")
    private long maxFileSizeBytes;

    /**
     * File extensions that we consider parseable source/config files.
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            // JVM
            "java", "kt", "kts", "groovy", "scala",
            // Web
            "js", "jsx", "ts", "tsx", "html", "css", "scss", "sass", "less", "vue", "svelte",
            // Python
            "py",
            // Systems
            "rs", "go", "c", "cpp", "h", "hpp", "cs",
            // Ruby
            "rb",
            // Config / Data
            "json", "xml", "yml", "yaml", "toml", "ini", "cfg", "env",
            "properties", "gradle",
            // SQL
            "sql",
            // Shell
            "sh", "bash", "bat", "ps1",
            // Docs
            "md", "txt", "rst",
            // Docker / CI
            "dockerfile"
    );

    /**
     * Directory names to skip entirely when walking the file tree.
     */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg",
            "node_modules", ".next", "out",
            "target", "build", "dist", "bin", "obj",
            "vendor", ".mvn", "gradle",
            ".idea", ".vscode", ".eclipse", ".settings",
            ".gradle", "__pycache__", ".mypy_cache", ".pytest_cache",
            ".tox", "venv", ".venv", "env", ".cache", "tmp", "temp",
            "coverage", ".nyc_output", "fixtures", "mocks", "surefire-reports"
    );

    /**
     * Specific filenames (lockfiles, generated manifests) to skip.
     */
    private static final Set<String> EXCLUDED_FILENAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json",
            "cargo.lock", "composer.lock", "gradle.lockfile", "poetry.lock",
            "pipfile.lock", "gemfile.lock", "tsconfig.tsbuildinfo"
    );

    /**
     * Extension → language name mapping for the language field.
     */
    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"), Map.entry("kts", "kotlin"),
            Map.entry("groovy", "groovy"), Map.entry("scala", "scala"),
            Map.entry("js", "javascript"), Map.entry("jsx", "javascript"),
            Map.entry("ts", "typescript"), Map.entry("tsx", "typescript"),
            Map.entry("py", "python"),
            Map.entry("rs", "rust"), Map.entry("go", "go"),
            Map.entry("c", "c"), Map.entry("cpp", "cpp"), Map.entry("h", "c"), Map.entry("hpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("rb", "ruby"),
            Map.entry("html", "html"), Map.entry("css", "css"),
            Map.entry("scss", "scss"), Map.entry("sass", "sass"), Map.entry("less", "less"),
            Map.entry("vue", "vue"), Map.entry("svelte", "svelte"),
            Map.entry("json", "json"), Map.entry("xml", "xml"),
            Map.entry("yml", "yaml"), Map.entry("yaml", "yaml"),
            Map.entry("toml", "toml"), Map.entry("ini", "ini"),
            Map.entry("properties", "properties"), Map.entry("gradle", "gradle"),
            Map.entry("sql", "sql"),
            Map.entry("sh", "shell"), Map.entry("bash", "shell"),
            Map.entry("bat", "batch"), Map.entry("ps1", "powershell"),
            Map.entry("md", "markdown"), Map.entry("txt", "text"), Map.entry("rst", "restructuredtext"),
            Map.entry("dockerfile", "dockerfile")
    );

    public FileParserService(RepositoryFileRepository repositoryFileRepository) {
        this.repositoryFileRepository = repositoryFileRepository;
    }

    /**
     * Walks the cloned repository directory, reads all supported source/config files,
     * and persists them as RepositoryFile rows.
     *
     * @param repository the Repository entity to associate files with
     * @param cloneDir   the path to the cloned repository on disk
     * @return the number of files parsed and saved
     */
    @Transactional
    public int parseRepository(Repository repository, Path cloneDir) {
        log.info("[PARSE] Starting file parsing for '{}' in {}", repository.getName(), cloneDir);
        long startTime = System.currentTimeMillis();

        List<RepositoryFile> filesToSave = new ArrayList<>();
        int[] counters = {0, 0, 0}; // [0]=parsed, [1]=skippedSize, [2]=skippedBinary

        try {
            Files.walkFileTree(cloneDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (EXCLUDED_DIRECTORIES.contains(dirName.toLowerCase())) {
                        log.debug("[PARSE] Skipping excluded directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString().toLowerCase();

                    // Skip lockfiles and machine-generated manifests
                    if (EXCLUDED_FILENAMES.contains(fileName)) {
                        counters[2]++;
                        return FileVisitResult.CONTINUE;
                    }

                    // Skip minified JavaScript/CSS and source maps
                    if (fileName.endsWith(".min.js") || fileName.endsWith(".min.css")
                            || fileName.endsWith(".bundle.js") || fileName.endsWith(".map")
                            || fileName.endsWith(".d.ts")) {
                        counters[2]++;
                        return FileVisitResult.CONTINUE;
                    }

                    String extension = getExtension(file);

                    // Skip unsupported extensions (binary files, images, etc.)
                    if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                        counters[2]++;
                        return FileVisitResult.CONTINUE;
                    }

                    // Tighten max file size thresholds: 50KB for data/config, 200KB for code
                    long maxAllowed = ("json".equals(extension) || "xml".equals(extension) || "yml".equals(extension) || "yaml".equals(extension) || "txt".equals(extension) || "sql".equals(extension))
                            ? 51200L : Math.min(maxFileSizeBytes, 204800L);

                    if (attrs.size() > maxAllowed) {
                        log.debug("[PARSE] Skipping oversized file ({} bytes): {}",
                                attrs.size(), file.getFileName());
                        counters[1]++;
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String relativePath = cloneDir.relativize(file).toString()
                                .replace("\\", "/"); // Normalize to forward slashes

                        RepositoryFile repoFile = RepositoryFile.builder()
                                .filePath(relativePath)
                                .fileName(file.getFileName().toString())
                                .language(LANGUAGE_MAP.getOrDefault(extension, extension))
                                .content(content)
                                .repository(repository)
                                .build();

                        filesToSave.add(repoFile);
                        counters[0]++;

                    } catch (IOException e) {
                        // Some files may fail to read as UTF-8 (binary masquerading as text)
                        log.debug("[PARSE] Could not read file as UTF-8, skipping: {}", file.getFileName());
                        counters[2]++;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("[PARSE] Failed to visit file: {} ({})", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("[PARSE] Error walking directory tree for '{}': {}",
                    repository.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to parse repository files", e);
        }

        // Batch save all files
        if (!filesToSave.isEmpty()) {
            repositoryFileRepository.saveAll(filesToSave);
            log.info("[PARSE] Saved {} files to database", filesToSave.size());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[PARSE] Completed parsing '{}' in {}ms — {} files parsed, {} skipped (oversized), {} skipped (unsupported)",
                repository.getName(), elapsed, counters[0], counters[1], counters[2]);

        return counters[0];
    }

    /**
     * Extracts the lowercase file extension from a path.
     * Handles special cases like "Dockerfile" (no extension).
     */
    private String getExtension(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();

        // Handle extensionless special files
        if (fileName.equals("dockerfile")) return "dockerfile";
        if (fileName.equals("makefile")) return "makefile";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return ""; // No extension
        }
        return fileName.substring(dotIndex + 1);
    }
}
