package io.perfoverlay.app;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Runs only in the Shizuku user process. Reads Binder dumps without spawning dumpsys. */
public final class ServiceDump implements AutoCloseable {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    public String read(String serviceName, String... args) throws Exception {
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, serviceName);
        if (service == null) throw new IllegalStateException(serviceName + " service unavailable");
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        Future<String> output = reader.submit(() -> {
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n > 2 * 1024 * 1024) throw new IllegalStateException("dump output exceeds limit");
                    bytes.write(buffer, 0, n);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        });
        try {
            service.dump(pipe[1].getFileDescriptor(), args);
            pipe[1].close();
            return output.get(2, TimeUnit.SECONDS);
        } finally {
            try { pipe[0].close(); } catch (Exception ignored) { }
            try { pipe[1].close(); } catch (Exception ignored) { }
            output.cancel(true);
        }
    }
    @Override public void close() { reader.shutdownNow(); }
}
