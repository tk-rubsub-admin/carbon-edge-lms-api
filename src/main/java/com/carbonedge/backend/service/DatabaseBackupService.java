package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.BackupFileResponse;
import com.carbonedge.backend.dto.BackupJobResponse;
import com.carbonedge.backend.dto.BackupOverviewResponse;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupService {
    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final AuthSessionRepository sessions;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "database-backup-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, MutableJob> jobs = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeId = new AtomicReference<>();

    @Value("${app.backup.path:./data/backups}") private String backupPath;
    @Value("${app.backup.encryption-key:}") private String encryptionKey;
    @Value("${app.backup.dump-command:mariadb-dump}") private String dumpCommand;
    @Value("${app.backup.elearning.host:be-db}") private String elearningHost;
    @Value("${app.backup.elearning.port:3306}") private int elearningPort;
    @Value("${app.backup.elearning.database:elearning}") private String elearningDatabase;
    @Value("${app.backup.elearning.username:elearning_user}") private String elearningUsername;
    @Value("${app.backup.elearning.password:}") private String elearningPassword;
    @Value("${app.backup.moodle.host:moodle-db}") private String moodleHost;
    @Value("${app.backup.moodle.port:3306}") private int moodlePort;
    @Value("${app.backup.moodle.database:moodle}") private String moodleDatabase;
    @Value("${app.backup.moodle.username:moodle}") private String moodleUsername;
    @Value("${app.backup.moodle.password:}") private String moodlePassword;

    public BackupOverviewResponse overview(String authorization) {
        requireAdmin(authorization);
        return new BackupOverviewResponse(isConfigured(), activeJob(), listFiles());
    }

    public BackupJobResponse request(String authorization) {
        requireAdmin(authorization);
        if (!isConfigured()) {
            throw new IllegalStateException("ตั้งค่า APP_BACKUP_ENCRYPTION_KEY และข้อมูลเชื่อมต่อฐานข้อมูลสำรองก่อนเริ่มงาน");
        }
        String id = RUN_STAMP.format(LocalDateTime.now(ZoneOffset.UTC)) + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (!activeId.compareAndSet(null, id)) {
            throw new ConflictException("มีงานสำรองข้อมูลกำลังทำงานอยู่แล้ว");
        }
        MutableJob job = new MutableJob(id, "RUNNING", "กำลังสำรองฐานข้อมูล elearning และ moodle", Instant.now(), null);
        jobs.put(id, job);
        trimJobs();
        executor.submit(() -> run(job));
        log.info("Database backup queued: jobId={}", id);
        return job.response();
    }

    public BackupJobResponse status(String authorization, String id) {
        requireAdmin(authorization);
        MutableJob job = jobs.get(id);
        if (job == null) throw new NotFoundException("ไม่พบงานสำรองข้อมูลนี้");
        return job.response();
    }

    public FileSystemResource download(String authorization, String id, String database) {
        requireAdmin(authorization);
        if (!validRunId(id) || !("elearning".equals(database) || "moodle".equals(database))) {
            throw new NotFoundException("ไม่พบไฟล์สำรองข้อมูล");
        }
        Path root = storageDirectory();
        Path file = root.resolve("backup-" + id).resolve(database + ".sql.gz.enc").normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new NotFoundException("ไม่พบไฟล์สำรองข้อมูล");
        return new FileSystemResource(file);
    }

    private void run(MutableJob job) {
        Path root = storageDirectory();
        Path pending = null;
        log.info("Database backup started: jobId={}", job.id);
        try {
            Files.createDirectories(root);
            setPrivatePermissions(root, true);
            pending = Files.createTempDirectory(root, ".pending-" + job.id + "-");
            setPrivatePermissions(pending, true);
            dumpDatabase(new DbTarget("elearning", elearningHost, elearningPort, elearningDatabase, elearningUsername, elearningPassword), pending.resolve("elearning.sql.gz.enc"));
            dumpDatabase(new DbTarget("moodle", moodleHost, moodlePort, moodleDatabase, moodleUsername, moodlePassword), pending.resolve("moodle.sql.gz.enc"));
            Map<String, Object> manifest = Map.of(
                    "id", job.id,
                    "createdAt", Instant.now().toString(),
                    "databases", List.of("elearning", "moodle"),
                    "encrypted", true,
                    "format", "sql.gz.aes-256-gcm");
            Files.writeString(pending.resolve("manifest.json"), objectMapper.writeValueAsString(manifest), StandardCharsets.UTF_8);
            Path completed = root.resolve("backup-" + job.id);
            moveAtomically(pending, completed);
            setPrivatePermissions(completed, true);
            job.complete("สำรองฐานข้อมูล elearning และ moodle สำเร็จ");
            log.info("Database backup completed: jobId={}", job.id);
        } catch (Exception exception) {
            if (pending != null) deleteTree(pending);
            job.fail("สำรองข้อมูลไม่สำเร็จ: " + safeMessage(exception));
            log.error("Database backup failed: jobId={}, exceptionType={}",
                    job.id, exception.getClass().getSimpleName(), exception);
        } finally {
            activeId.compareAndSet(job.id, null);
        }
    }

    private void dumpDatabase(DbTarget target, Path destination) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                dumpCommand,
                "--host=" + target.host,
                "--port=" + target.port,
                "--user=" + target.username,
                "--single-transaction",
                "--quick",
                "--routines",
                "--events",
                "--triggers",
                "--hex-blob",
                "--default-character-set=utf8mb4",
                "--databases",
                target.database));
        Path errorFile = Files.createTempFile(storageDirectory(), ".dump-error-", ".log");
        setPrivatePermissions(errorFile, false);
        Path tempFile = destination.resolveSibling(destination.getFileName() + ".part");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(errorFile.toFile());
            builder.environment().put("MYSQL_PWD", target.password);
            process = builder.start();
            writeEncryptedDump(process, tempFile);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("dump " + target.label + " exit=" + exitCode + ": " + readError(errorFile));
            }
            setPrivatePermissions(tempFile, false);
            moveAtomically(tempFile, destination);
        } catch (InterruptedException exception) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("งานสำรองถูกหยุดระหว่างทำ", exception);
        } finally {
            Files.deleteIfExists(errorFile);
            Files.deleteIfExists(tempFile);
        }
    }

    private void writeEncryptedDump(Process process, Path destination) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
        byte[] nonce = new byte[12];
        new java.security.SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, nonce));
        try (OutputStream raw = Files.newOutputStream(destination)) {
            raw.write(nonce);
            try (CipherOutputStream encrypted = new CipherOutputStream(raw, cipher);
                 java.util.zip.GZIPOutputStream compressed = new java.util.zip.GZIPOutputStream(encrypted)) {
                process.getInputStream().transferTo(compressed);
            }
        }
    }

    private List<BackupFileResponse> listFiles() {
        Path root = storageDirectory();
        if (!Files.isDirectory(root)) return List.of();
        List<BackupFileResponse> files = new ArrayList<>();
        try (Stream<Path> folders = Files.list(root)) {
            folders.filter(Files::isDirectory)
                    .filter(folder -> folder.getFileName().toString().matches("backup-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}"))
                    .forEach(folder -> {
                        String id = folder.getFileName().toString().substring("backup-".length());
                        for (String database : List.of("elearning", "moodle")) {
                            Path file = folder.resolve(database + ".sql.gz.enc");
                            if (!Files.isRegularFile(file)) continue;
                            try {
                                files.add(new BackupFileResponse(id, database, Files.size(file),
                                        Files.getLastModifiedTime(folder).toInstant()));
                            } catch (IOException ignored) { }
                        }
                    });
        } catch (IOException ignored) { }
        return files.stream().sorted(Comparator.comparing(BackupFileResponse::createdAt).reversed()).toList();
    }

    private BackupJobResponse activeJob() {
        String id = activeId.get();
        MutableJob job = id == null ? null : jobs.get(id);
        return job == null ? null : job.response();
    }

    private boolean isConfigured() {
        try {
            return elearningPassword != null && !elearningPassword.isBlank()
                    && moodlePassword != null && !moodlePassword.isBlank()
                    && Base64.getDecoder().decode(encryptionKey).length == 32
                    && dumpCommand != null && !dumpCommand.isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private AuthSession requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new UnauthorizedException("Missing bearer token");
        AuthSession session = sessions.findWithUserByAccessToken(authorization.substring(7).trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (!session.isActive()) throw new UnauthorizedException("Session has expired");
        if (session.getUser().getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required");
        return session;
    }

    private Path storageDirectory() { return Path.of(backupPath).toAbsolutePath().normalize(); }

    private boolean validRunId(String id) { return id != null && id.matches("[0-9]{8}-[0-9]{6}-[a-f0-9]{8}"); }

    private void setPrivatePermissions(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : PRIVATE_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException | IOException ignored) { }
    }

    private String readError(Path file) {
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8).trim();
            return value.length() > 1000 ? value.substring(0, 1000) : value;
        } catch (IOException exception) { return "อ่านรายละเอียด error ไม่ได้"; }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 1200 ? message.substring(0, 1200) : message;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private void trimJobs() {
        if (jobs.size() <= 100) return;
        jobs.values().stream().filter(job -> !"RUNNING".equals(job.status))
                .sorted(Comparator.comparing(job -> job.startedAt))
                .limit(jobs.size() - 100L)
                .forEach(job -> jobs.remove(job.id));
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    private record DbTarget(String label, String host, int port, String database, String username, String password) { }

    private static final class MutableJob {
        private final String id;
        private volatile String status;
        private volatile String message;
        private final Instant startedAt;
        private volatile Instant finishedAt;

        private MutableJob(String id, String status, String message, Instant startedAt, Instant finishedAt) {
            this.id = id; this.status = status; this.message = message; this.startedAt = startedAt; this.finishedAt = finishedAt;
        }
        private void complete(String result) { status = "COMPLETED"; message = result; finishedAt = Instant.now(); }
        private void fail(String result) { status = "FAILED"; message = result; finishedAt = Instant.now(); }
        private BackupJobResponse response() { return new BackupJobResponse(id, status, message, startedAt, finishedAt); }
    }
}
