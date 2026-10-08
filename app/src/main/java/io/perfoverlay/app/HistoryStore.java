package io.perfoverlay.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Private, versioned UTF-8 recordings. Mutations are serialized by History. */
public final class HistoryStore {
    public static final int MAX_POINTS = 21600;
    private final File directory;
    public volatile String active = "";
    private long origin, sequence, lastTime;
    private int count;
    public static final class Point {
        public final long sequence, elapsed, wall;
        public final Map<String,String> values;
        public Point(long sequence, long elapsed, long wall, Map<String,String> values) {
            this.sequence = sequence; this.elapsed = elapsed; this.wall = wall;
            this.values = new LinkedHashMap<>(values);
        }
    }
    public HistoryStore(File directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory.toPath());
    }
    private File file(String id) {
        if (!id.matches("[0-9]+-[a-f0-9-]+\\.mrt")) throw new IllegalArgumentException("无效记录");
        return new File(directory, id);
    }
    public synchronized String begin(long wall, long elapsed) throws IOException {
        if (!active.isEmpty()) return active;
        long bytes = 0;
        for (String id : list()) bytes += file(id).length();
        if (bytes >= 256L * 1024 * 1024) throw new IOException("记录已占用 256 MiB，请先删除不需要的记录");
        String id = wall + "-" + UUID.randomUUID() + ".mrt";
        Files.write(file(id).toPath(), ("MRTSS1\t" + wall + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        origin = elapsed; sequence = 0; count = 0; lastTime = -1; active = id;
        return id;
    }
    public synchronized void end() { active = ""; }
    public synchronized void gap() { sequence++; }
    public synchronized boolean append(String expected, long elapsed, long wall, Map<String,String> values) throws IOException {
        if (active.isEmpty() || !active.equals(expected) || elapsed < origin) return false;
        long relative = elapsed - origin;
        if (lastTime >= 0 && relative - lastTime < 1000) return false;
        if (count >= MAX_POINTS) { end(); throw new IOException("本次记录已达到 21600 个采样点，已停止记录"); }
        Point point = new Point(sequence++, relative, wall, values);
        try (FileOutputStream out = new FileOutputStream(file(active), true)) {
            out.write(encode(point).getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
        count++; lastTime = relative; return true;
    }
    public synchronized List<String> list() {
        String[] names = directory.list((dir, name) -> name.matches("[0-9]+-[a-f0-9-]+\\.mrt"));
        List<String> result = new ArrayList<>();
        if (names != null) Collections.addAll(result, names);
        result.sort(Collections.reverseOrder()); return result;
    }
    public static long started(String id) { return Long.parseLong(id.substring(0, id.indexOf('-'))); }
    public synchronized List<Point> read(String id) throws IOException {
        List<Point> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file(id).toPath(), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("MRTSS1\t")) throw new IOException("不支持的记录格式");
            String line;
            while ((line = reader.readLine()) != null) {
                try { Point p = decode(line); if (p != null) result.add(p); }
                catch (RuntimeException ignored) { /* A process death can leave an incomplete final sample. */ }
            }
        }
        return result;
    }
    private void editable(String id) {
        if (id.equals(active)) throw new IllegalStateException("请先结束本次记录，再删除数据");
    }
    public synchronized void delete(String id) throws IOException { editable(id); Files.delete(file(id).toPath()); }
    /** Inclusive elapsed-time range, or exactly one sequence; original sequence/time retained. */
    public synchronized int deletePoints(String id, long from, long to, Long onlySequence) throws IOException {
        editable(id);
        if (from < 0 || to < from) throw new IllegalArgumentException("结束时间不能早于开始时间");
        List<Point> all = read(id); int before = all.size();
        all.removeIf(p -> onlySequence != null ? p.sequence == onlySequence : p.elapsed >= from && p.elapsed <= to);
        if (before == all.size()) return 0;
        Path original = file(id).toPath(), temporary = directory.toPath().resolve(id + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
                out.write(("MRTSS1\t" + started(id) + "\n").getBytes(StandardCharsets.UTF_8));
                for (Point p : all) out.write(encode(p).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // Same-directory atomic replacement: a failed edit leaves the old recording intact.
            Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return before - all.size();
    }
    private static String encode(Point p) {
        StringBuilder b = new StringBuilder().append(p.sequence).append('\t').append(p.elapsed).append('\t').append(p.wall);
        Base64.Encoder encoder = Base64.getEncoder();
        for (Map.Entry<String,String> e : p.values.entrySet()) {
            if (!e.getKey().matches("[A-Z_]+")) continue;
            b.append('\t').append(e.getKey()).append('=').append(encoder.encodeToString(e.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return b.append("\tEND\n").toString();
    }
    private static Point decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 4 || !parts[parts.length - 1].equals("END")) return null;
        Map<String,String> values = new LinkedHashMap<>();
        for (int i = 3; i < parts.length - 1; i++) {
            int equals = parts[i].indexOf('=');
            values.put(parts[i].substring(0, equals), new String(Base64.getDecoder().decode(parts[i].substring(equals + 1)), StandardCharsets.UTF_8));
        }
        return new Point(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]), values);
    }
}
