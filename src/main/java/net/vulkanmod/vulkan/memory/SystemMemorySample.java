package net.vulkanmod.vulkan.memory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read only the two Linux counters used by the adaptive memory-pressure guard. */
public record SystemMemorySample(long availableMiB, long totalMiB) {
    public static SystemMemorySample read(Path path) throws IOException {
        if(!Files.isReadable(path)) return new SystemMemorySample(-1L, -1L);
        try(BufferedReader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        }
    }

    static SystemMemorySample parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered
                ? buffered : new BufferedReader(source);
        long available = -1L, total = -1L;
        String line;
        while((line = reader.readLine()) != null) {
            if(line.startsWith("MemAvailable:")) available = parseMiB(line, 13);
            else if(line.startsWith("MemTotal:")) total = parseMiB(line, 9);
            if(available >= 0L && total >= 0L) break;
        }
        return new SystemMemorySample(available, total);
    }

    private static long parseMiB(String line, int start) {
        String tail = line.substring(start).trim();
        int space = tail.indexOf(' ');
        String number = space >= 0 ? tail.substring(0, space) : tail;
        try {
            long kb = Long.parseLong(number);
            return kb < 0L ? -1L : kb / 1024L;
        } catch(NumberFormatException malformed) {
            return -1L;
        }
    }
}
