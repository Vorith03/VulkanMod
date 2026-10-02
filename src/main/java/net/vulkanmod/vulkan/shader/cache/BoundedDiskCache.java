package net.vulkanmod.vulkan.shader.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Disposable, checksummed results. Never a source of rendering state. */
public final class BoundedDiskCache {
    private static final int MAGIC = 0x564d4331;
    private static final int HEADER = 76;
    private final Path directory;
    private final int maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxEntries;

    public BoundedDiskCache(Path directory, int maxEntryBytes, long maxTotalBytes, int maxEntries) {
        this.directory = directory;
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxEntries = maxEntries;
    }

    public static byte[] digest(byte[] bytes) { return sha256().digest(bytes); }
    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private Path path(byte[] key) {
        if(key.length != 32) throw new IllegalArgumentException("Cache key must be SHA-256");
        return directory.resolve(HexFormat.of().formatHex(key) + ".bin");
    }

    public synchronized byte[] read(byte[] key) throws IOException {
        Path file = path(key);
        if(!Files.isRegularFile(file)) return null;
        long size = Files.size(file);
        if(size < HEADER || size > (long)maxEntryBytes + HEADER) return null;
        // Bound even a concurrently replaced/grown file before allocating its contents.
        byte[] encoded;
        try(var input = Files.newInputStream(file)) {
            encoded = input.readNBytes(maxEntryBytes + HEADER + 1);
        }
        if(encoded.length < HEADER || encoded.length > maxEntryBytes + HEADER) return null;
        ByteBuffer in = ByteBuffer.wrap(encoded);
        if(in.getInt() != MAGIC || in.getInt() != 1) return null;
        int length = in.getInt();
        if(length <= 0 || length != encoded.length - HEADER) return null;
        byte[] storedKey = new byte[32], checksum = new byte[32];
        in.get(storedKey).get(checksum);
        if(!MessageDigest.isEqual(key, storedKey)) return null;
        byte[] payload = new byte[length];
        in.get(payload);
        return MessageDigest.isEqual(checksum, digest(payload)) ? payload : null;
    }

    public synchronized boolean write(byte[] key, byte[] payload) throws IOException {
        if(payload.length == 0 || payload.length > maxEntryBytes
                || (long)payload.length + HEADER > maxTotalBytes) return false;
        Path file = path(key);
        Files.createDirectories(directory);
        // Admission rather than deleting another process's live cache results.
        // Once full, old entries still work and new shaders simply compile normally.
        List<Path> entries;
        try(var stream = Files.list(directory)) {
            entries = stream.filter(p -> p.getFileName().toString().matches("[0-9a-f]{64}\\.bin"))
                    .limit((long)maxEntries + 1).toList();
        }
        long total = 0;
        boolean replacing = false;
        for(Path entry : entries) {
            if(entry.equals(file)) replacing = true;
            else if(Files.isRegularFile(entry)) total += Files.size(entry);
        }
        if(entries.size() - (replacing ? 1 : 0) >= maxEntries
                || total + HEADER + payload.length > maxTotalBytes) return false;
        ByteBuffer encoded = ByteBuffer.allocate(HEADER + payload.length);
        encoded.putInt(MAGIC).putInt(1).putInt(payload.length).put(key).put(digest(payload)).put(payload);
        Path temporary = Files.createTempFile(directory, "cache-", ".tmp");
        try {
            Files.write(temporary, encoded.array());
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
        return true;
    }
}
