package media.whitewhale.iduo.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-owned streaming provider whose individual reads can be released after recreation. */
public final class BlockingContentProvider extends ContentProvider {
    private static volatile byte[] payload = new byte[0];
    private static final AtomicInteger reads = new AtomicInteger();
    private static final AtomicInteger completed = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, CountDownLatch> gates = new ConcurrentHashMap<>();

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String argument, Bundle extras) {
        Bundle result = new Bundle();
        if ("reset".equals(method)) {
            payload = extras.getString("payload", "").getBytes(StandardCharsets.UTF_8);
            reads.set(0);
            completed.set(0);
            gates.clear();
        } else if ("release".equals(method)) {
            int ordinal = Integer.parseInt(argument);
            gates.computeIfAbsent(ordinal, ignored -> new CountDownLatch(1)).countDown();
        }
        result.putInt("reads", reads.get());
        result.putInt("completed", completed.get());
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"payload".equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            int ordinal = reads.incrementAndGet();
            CountDownLatch gate = gates.computeIfAbsent(ordinal, ignored -> new CountDownLatch(1));
            byte[] snapshot = payload;
            new Thread(() -> {
                try (FileOutputStream output = new FileOutputStream(pipe[1].getFileDescriptor())) {
                    gate.await();
                    output.write(snapshot);
                } catch (Exception ignored) {
                } finally {
                    completed.incrementAndGet();
                    try { pipe[1].close(); } catch (Exception ignored) { }
                }
            }, "duo-blocking-provider-" + ordinal).start();
            return pipe[0];
        } catch (java.io.IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
