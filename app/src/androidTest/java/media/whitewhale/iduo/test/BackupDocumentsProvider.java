package media.whitewhale.iduo.test;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;

/** A single-file SAF root isolated inside the instrumentation APK's private storage. */
public final class BackupDocumentsProvider extends DocumentsProvider {
    private static final String ROOT = "root";
    private static final String DOCUMENT = "layout";
    private static final String[] ROOT_COLUMNS = {
        DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_MIME_TYPES, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };
    private static final String[] DOCUMENT_COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    @Override public boolean onCreate() { return true; }
    private File file() { return new File(getContext().getFilesDir(), "saf-layout.json"); }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        MatrixCursor.RowBuilder row = result.newRow();
        add(row, DocumentsContract.Root.COLUMN_ROOT_ID, ROOT);
        add(row, DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT);
        add(row, DocumentsContract.Root.COLUMN_TITLE, "Duo Backup Fixture");
        add(row, DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_LOCAL_ONLY);
        add(row, DocumentsContract.Root.COLUMN_MIME_TYPES, "application/json\ntext/plain");
        add(row, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, 2L * 1024 * 1024);
        return result;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        include(result, documentId);
        return result;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        if (!ROOT.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        if (file().isFile()) include(result, DOCUMENT);
        return result;
    }

    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        if (!ROOT.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        try { if (!file().exists() && !file().createNewFile()) throw new FileNotFoundException("create failed"); }
        catch (java.io.IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return DOCUMENT;
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if (!DOCUMENT.equals(documentId)) throw new FileNotFoundException(documentId);
        file().delete();
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (!DOCUMENT.equals(documentId) || (!file().exists() && !mode.contains("w"))) throw new FileNotFoundException(documentId);
        return ParcelFileDescriptor.open(file(), ParcelFileDescriptor.parseMode(mode));
    }

    private void include(MatrixCursor cursor, String id) throws FileNotFoundException {
        MatrixCursor.RowBuilder row = cursor.newRow();
        if (ROOT.equals(id)) {
            add(row, DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT);
            add(row, DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Duo Backup Fixture");
            add(row, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
            add(row, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
        } else if (DOCUMENT.equals(id) && file().isFile()) {
            add(row, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOCUMENT);
            add(row, DocumentsContract.Document.COLUMN_DISPLAY_NAME, "duo-launcher-layout.json");
            add(row, DocumentsContract.Document.COLUMN_MIME_TYPE, "application/json");
            add(row, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
            add(row, DocumentsContract.Document.COLUMN_SIZE, file().length());
            add(row, DocumentsContract.Document.COLUMN_LAST_MODIFIED, file().lastModified());
        } else throw new FileNotFoundException(id);
    }

    private static void add(MatrixCursor.RowBuilder row, String column, Object value) {
        try { row.add(column, value); } catch (IllegalArgumentException ignored) { }
    }
}
