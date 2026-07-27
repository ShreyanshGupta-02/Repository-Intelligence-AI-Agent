package com.github.repository_ai.service;

import com.github.repository_ai.entity.CodeChunk;
import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.entity.RepositoryFile;
import com.github.repository_ai.repository.CodeChunkRepository;
import com.github.repository_ai.repository.RepositoryFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Splits parsed source files into semantically meaningful code chunks
 * for embedding and retrieval.
 * <p>
 * Strategy:
 * <ol>
 *   <li>For code files (Java, Python, JS/TS, etc.) — detect structural boundaries
 *       (method/function/class definitions) and split at those boundaries.</li>
 *   <li>For non-code files (markdown, config, YAML, etc.) — use a sliding window
 *       with overlap.</li>
 *   <li>Oversized structural chunks (very long methods) are further split with
 *       sliding window + overlap to keep chunks within the max size.</li>
 * </ol>
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    // ==================== Configuration ====================

    /** Target chunk size in lines. */
    private static final int TARGET_CHUNK_SIZE = 100;
    /** Maximum chunk size — chunks bigger than this get split. */
    private static final int MAX_CHUNK_SIZE = 150;
    /** Maximum allowed characters in a chunk content to prevent embedding token overflow. */
    private static final int MAX_CHUNK_CHARS = 3000;
    /** Overlap lines for sliding window (prevents losing context at boundaries). */
    private static final int OVERLAP_SIZE = 15;
    /** Minimum chunk size — don't create tiny chunks. */
    private static final int MIN_CHUNK_SIZE = 8;

    // ==================== Language Sets ====================

    /** Languages that support structural (method/class boundary) chunking. */
    private static final Set<String> STRUCTURAL_LANGUAGES = Set.of(
            "java", "kotlin", "csharp", "scala", "groovy",
            "javascript", "typescript",
            "python",
            "go", "rust",
            "ruby",
            "c", "cpp"
    );

    // ==================== Boundary Detection Patterns ====================

    /** Java/Kotlin/C#/Scala: method or class definitions. */
    private static final Pattern JAVA_BOUNDARY = Pattern.compile(
            "^\\s*(public|private|protected|static|final|abstract|override|synchronized|default|" +
            "open|internal|sealed|data|inline|suspend|fun|class|interface|enum|record|object)\\s+.*"
    );

    /** Python: def or class. */
    private static final Pattern PYTHON_BOUNDARY = Pattern.compile(
            "^\\s{0,4}(def|class|async\\s+def)\\s+\\w+"
    );

    /** JavaScript/TypeScript: function, class, const/let arrow functions, export. */
    private static final Pattern JS_TS_BOUNDARY = Pattern.compile(
            "^\\s{0,4}(export\\s+)?(default\\s+)?(async\\s+)?" +
            "(function\\*?|class|const|let|var)\\s+\\w+.*"
    );

    /** Go: func declarations. */
    private static final Pattern GO_BOUNDARY = Pattern.compile(
            "^func\\s+.*"
    );

    /** Rust: fn, struct, enum, impl, trait. */
    private static final Pattern RUST_BOUNDARY = Pattern.compile(
            "^\\s*(pub(\\(crate\\))?\\s+)?(fn|struct|enum|impl|trait|mod|type)\\s+.*"
    );

    /** Ruby: def, class, module. */
    private static final Pattern RUBY_BOUNDARY = Pattern.compile(
            "^\\s*(def|class|module)\\s+\\w+.*"
    );

    /** C/C++: function-like patterns (return_type function_name(...)). */
    private static final Pattern C_CPP_BOUNDARY = Pattern.compile(
            "^\\s*(static\\s+|inline\\s+|extern\\s+|virtual\\s+|const\\s+)*" +
            "(void|int|char|float|double|long|bool|auto|unsigned|signed|struct|class|enum|" +
            "std::\\w+|\\w+_t|\\w+)\\s+\\*?\\w+\\s*\\(.*"
    );

    // ==================== Dependencies ====================

    private final RepositoryFileRepository repositoryFileRepository;
    private final CodeChunkRepository codeChunkRepository;

    public ChunkingService(RepositoryFileRepository repositoryFileRepository,
                           CodeChunkRepository codeChunkRepository) {
        this.repositoryFileRepository = repositoryFileRepository;
        this.codeChunkRepository = codeChunkRepository;
    }

    // ==================== Public API ====================

    /**
     * Chunks all files belonging to a repository and persists the chunks.
     *
     * @param repository the repository whose files should be chunked
     * @return total number of chunks created
     */
    @Transactional
    public int chunkRepository(Repository repository) {
        log.info("[CHUNK] Starting chunking for repository '{}'", repository.getName());
        long startTime = System.currentTimeMillis();

        List<RepositoryFile> files = repositoryFileRepository.findAllByRepositoryId(repository.getId());
        log.debug("[CHUNK] Found {} files to chunk for '{}'", files.size(), repository.getName());

        List<CodeChunk> allChunks = new ArrayList<>();
        int structuralCount = 0;
        int slidingWindowCount = 0;

        for (RepositoryFile file : files) {
            if (file.getContent() == null || file.getContent().isBlank()) {
                log.debug("[CHUNK] Skipping empty file: {}", file.getFilePath());
                continue;
            }

            String[] lines = file.getContent().split("\n", -1);

            // Files too small to chunk — store as single chunk
            if (lines.length <= MIN_CHUNK_SIZE) {
                allChunks.add(createChunk(file, file.getContent(), 1, lines.length));
                continue;
            }

            List<CodeChunk> fileChunks;
            String language = file.getLanguage() != null ? file.getLanguage().toLowerCase() : "";

            if (STRUCTURAL_LANGUAGES.contains(language)) {
                fileChunks = structuralChunk(file, lines, language);
                structuralCount += fileChunks.size();
            } else {
                fileChunks = slidingWindowChunk(file, lines, 0, lines.length - 1);
                slidingWindowCount += fileChunks.size();
            }

            allChunks.addAll(fileChunks);
        }

        // Batch save all chunks
        if (!allChunks.isEmpty()) {
            codeChunkRepository.saveAll(allChunks);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[CHUNK] Completed chunking '{}' in {}ms — {} total chunks ({} structural, {} sliding-window) from {} files",
                repository.getName(), elapsed, allChunks.size(), structuralCount, slidingWindowCount, files.size());

        return allChunks.size();
    }

    // ==================== Structural Chunking ====================

    /**
     * Chunks a code file by splitting at structural boundaries (method/class definitions).
     * If a structural block exceeds MAX_CHUNK_SIZE, it's further split with sliding window.
     */
    private List<CodeChunk> structuralChunk(RepositoryFile file, String[] lines, String language) {
        // Find boundary line indices (0-indexed)
        List<Integer> boundaries = findBoundaries(lines, language);

        if (boundaries.isEmpty()) {
            // No structural boundaries found — fall back to sliding window
            log.debug("[CHUNK] No structural boundaries in '{}', using sliding window", file.getFilePath());
            return slidingWindowChunk(file, lines, 0, lines.length - 1);
        }

        List<CodeChunk> chunks = new ArrayList<>();

        // Handle preamble (imports, package declaration, etc.) before first boundary
        if (boundaries.get(0) > 0) {
            int preambleEnd = boundaries.get(0) - 1;
            if (preambleEnd - 0 + 1 >= MIN_CHUNK_SIZE) {
                chunks.add(createChunk(file, joinLines(lines, 0, preambleEnd), 1, preambleEnd + 1));
            }
        }

        // Process each structural boundary
        for (int i = 0; i < boundaries.size(); i++) {
            int blockStart = boundaries.get(i);
            int blockEnd = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) - 1 : lines.length - 1;

            int blockSize = blockEnd - blockStart + 1;

            if (blockSize > MAX_CHUNK_SIZE) {
                // Oversized block (very long method) — split with sliding window
                log.debug("[CHUNK] Oversized block ({} lines) at line {} in '{}', splitting",
                        blockSize, blockStart + 1, file.getFilePath());
                chunks.addAll(slidingWindowChunk(file, lines, blockStart, blockEnd));
            } else if (blockSize >= MIN_CHUNK_SIZE) {
                chunks.add(createChunk(file, joinLines(lines, blockStart, blockEnd),
                        blockStart + 1, blockEnd + 1)); // Convert to 1-indexed
            }
        }

        return chunks;
    }

    /**
     * Finds structural boundary line indices for a given language.
     * Returns 0-indexed line numbers where new structural blocks begin.
     */
    private List<Integer> findBoundaries(String[] lines, String language) {
        Pattern pattern = getBoundaryPattern(language);
        if (pattern == null) return List.of();

        List<Integer> boundaries = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Skip empty lines and comments
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")
                    || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }

            if (pattern.matcher(line).matches()) {
                boundaries.add(i);
            }
        }

        return boundaries;
    }

    /**
     * Returns the appropriate boundary detection regex for a language.
     */
    private Pattern getBoundaryPattern(String language) {
        return switch (language) {
            case "java", "kotlin", "csharp", "scala", "groovy" -> JAVA_BOUNDARY;
            case "python" -> PYTHON_BOUNDARY;
            case "javascript", "typescript" -> JS_TS_BOUNDARY;
            case "go" -> GO_BOUNDARY;
            case "rust" -> RUST_BOUNDARY;
            case "ruby" -> RUBY_BOUNDARY;
            case "c", "cpp" -> C_CPP_BOUNDARY;
            default -> null;
        };
    }

    // ==================== Sliding Window Chunking ====================

    /**
     * Splits lines[start..end] into chunks using a sliding window with overlap.
     * Used for non-code files and as a fallback for oversized structural blocks.
     */
    private List<CodeChunk> slidingWindowChunk(RepositoryFile file, String[] lines, int start, int end) {
        List<CodeChunk> chunks = new ArrayList<>();
        int totalLines = end - start + 1;

        if (totalLines <= TARGET_CHUNK_SIZE) {
            // Small enough for a single chunk
            chunks.add(createChunk(file, joinLines(lines, start, end),
                    start + 1, end + 1));
            return chunks;
        }

        int currentStart = start;
        while (currentStart <= end) {
            int currentEnd = Math.min(currentStart + TARGET_CHUNK_SIZE - 1, end);

            chunks.add(createChunk(file, joinLines(lines, currentStart, currentEnd),
                    currentStart + 1, currentEnd + 1));

            // Advance by (TARGET_CHUNK_SIZE - OVERLAP_SIZE) to create overlap
            currentStart += TARGET_CHUNK_SIZE - OVERLAP_SIZE;

            // If the remaining lines are too few, we're done (already captured in the last chunk)
            if (end - currentStart + 1 < MIN_CHUNK_SIZE) {
                break;
            }
        }

        return chunks;
    }

    // ==================== Helpers ====================

    /**
     * Creates a CodeChunk entity.
     *
     * @param startLine 1-indexed starting line number
     * @param endLine   1-indexed ending line number
     */
    private CodeChunk createChunk(RepositoryFile file, String content, int startLine, int endLine) {
        String safeContent = content;
        if (safeContent != null && safeContent.length() > MAX_CHUNK_CHARS) {
            safeContent = safeContent.substring(0, MAX_CHUNK_CHARS) + "\n... [truncated for embedding size limit]";
        }

        return CodeChunk.builder()
                .content(safeContent)
                .startLine(startLine)
                .endLine(endLine)
                .repositoryFile(file)
                .build();
    }

    /**
     * Joins lines[start..end] (inclusive, 0-indexed) into a single string.
     */
    private String joinLines(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
