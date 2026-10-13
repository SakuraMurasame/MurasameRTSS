package io.perfoverlay.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

/** Separate bounded, checksummed charge records; never modifies the existing performance history. */
public final class AppBatteryStore {
    private static final byte[] HEADER = "MRTBAT1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_ROW = 2 * 1024 * 1024, MAX_SAMPLES = 1441;
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private final File folder;
    private String active = "";
    private int count;
    private long origin = -1;
    public AppBatteryStore(File folder) throws IOException {
        this.folder = folder; if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Storage unavailable");
    }
    public synchronized String begin(long wall) throws IOException {
        String id = wall + "-" + UUID.randomUUID() + ".mrb";
        try (FileOutputStream out = new FileOutputStream(file(id))) { out.write(HEADER); out.getFD().sync(); }
        active = id; count = 0; origin = -1; return id;
    }
    public synchronized void end() { active = ""; }
    public synchronized void append(String expected, AppBatteryData.Sample sample) throws IOException {
        if (active.isEmpty() || !active.equals(expected)) return;
        if (origin < 0) origin = sample.elapsed;
        if (count >= MAX_SAMPLES || sample.elapsed - origin > 24 * 3600_000L) throw new IOException("Record limit reached");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bytes)) {
            d.writeLong(sample.wall); d.writeLong(sample.elapsed); d.writeBoolean(sample.valid); d.writeUTF(sample.epoch);
            d.writeDouble(sample.capacity); d.writeDouble(sample.computed); d.writeDouble(sample.volts); d.writeInt(sample.apps.size());
            for (Map.Entry<Integer,AppBatteryData.Usage> entry : sample.apps.entrySet()) {
                AppBatteryData.Usage u = entry.getValue(); d.writeInt(entry.getKey()); d.writeDouble(u.total);
                d.writeDouble(u.foreground); d.writeDouble(u.background); d.writeInt(u.packages.size());
                for (String name : u.packages) d.writeUTF(name);
            }
        }
        byte[] row = bytes.toByteArray(); if (row.length > MAX_ROW) throw new IOException("Sample too large");
        if (file(active).length() + row.length + 8 > MAX_BYTES) throw new IOException("Record limit reached");
        CRC32 crc = new CRC32(); crc.update(row);
        try (FileOutputStream f = new FileOutputStream(file(active), true); DataOutputStream out = new DataOutputStream(f)) {
            out.writeInt(row.length); out.write(row); out.writeInt((int)crc.getValue()); out.flush(); f.getFD().sync();
        }
        count++;
    }
    public synchronized List<String> list() {
        String[] names = folder.list((f, n) -> n.matches("[0-9]+-[a-f0-9-]+\\.mrb"));
        List<String> result = new ArrayList<>(); if (names != null) Collections.addAll(result, names);
        result.sort(Collections.reverseOrder()); return result;
    }
    public synchronized List<AppBatteryData.Sample> read(String id) throws IOException {
        List<AppBatteryData.Sample> result = new ArrayList<>();
        Map<String,String> identities = new HashMap<>();
        if (file(id).length() > MAX_BYTES) throw new IOException("Record too large");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file(id)))) {
            byte[] header = new byte[HEADER.length]; in.readFully(header);
            if (!Arrays.equals(header, HEADER)) throw new IOException("Unsupported record");
            while (in.available() > 0 && result.size() < MAX_SAMPLES) {
                try {
                    int length = in.readInt(); if (length <= 0 || length > MAX_ROW) throw new IOException("Invalid sample length");
                    byte[] row = new byte[length]; in.readFully(row); int checksum = in.readInt(); CRC32 crc = new CRC32(); crc.update(row);
                    if ((int)crc.getValue() != checksum) throw new IOException("Sample checksum mismatch");
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(row))) {
                        long wall = d.readLong(), elapsed = d.readLong(); boolean valid = d.readBoolean(); String epoch = d.readUTF();
                        double capacity = d.readDouble(), computed = d.readDouble(), volts = d.readDouble(); int size = d.readInt();
                        if (size < 0 || size > 2048 || elapsed < 0 || wall < 0) throw new IOException("Invalid sample");
                        if (valid && (!Double.isFinite(capacity + computed) || capacity <= 0 || computed < 0 || epoch.isEmpty())) throw new IOException("Invalid attribution sample");
                        Map<Integer,AppBatteryData.Usage> apps = new TreeMap<>();
                        for (int i = 0; i < size; i++) {
                            int uid = d.readInt(); double total = d.readDouble(), fg = d.readDouble(), bg = d.readDouble(); int packages = d.readInt();
                            if (uid < 0 || !Double.isFinite(total) || total < 0 || packages < 0 || packages > 128) throw new IOException("Invalid UID sample");
                            List<String> names = new ArrayList<>();
                            for (int j = 0; j < packages; j++) {
                                String name = d.readUTF(); names.add(identities.computeIfAbsent(name, key -> key));
                            }
                            apps.put(uid, new AppBatteryData.Usage(total, fg, bg, names));
                        }
                        if (d.available() != 0) throw new IOException("Unexpected sample data");
                        result.add(new AppBatteryData.Sample(wall, elapsed, epoch, capacity, computed, volts, valid, apps));
                    }
                } catch (EOFException incompleteTail) { break; }
            }
        }
        return result;
    }
    public synchronized void delete(String id) throws IOException {
        if (id.equals(active)) throw new IllegalStateException("Active record"); Files.delete(file(id).toPath());
    }
    private File file(String id) {
        if (!id.matches("[0-9]+-[a-f0-9-]+\\.mrb")) throw new IllegalArgumentException("Invalid record ID"); return new File(folder, id);
    }
}
