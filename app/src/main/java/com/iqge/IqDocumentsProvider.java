package com.iqge;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * DocumentsProvider exposing the IQ Code HOME ({@code <app files>/home}) to document-aware file
 * managers. The tree is read-write through the standard SAF entry points, but the private state
 * directories ({@value #PRIVATE_DIR_IQ}, {@value #PRIVATE_DIR_TERMUX}) are never listed, opened or
 * writable: they hold session history, agent configuration and the Termux userland metadata.
 */
public final class IqDocumentsProvider extends DocumentsProvider {
    private static final String ROOT_ID = "iq-home";
    private static final String ROOT_DOCUMENT_ID = "home";
    private static final String PRIVATE_DIR_IQ = ".iq";
    private static final String PRIVATE_DIR_TERMUX = ".termux";

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection == null ? new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        } : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID);
        row.add(DocumentsContract.Root.COLUMN_TITLE, "元境 HOME");
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            | DocumentsContract.Root.FLAG_SUPPORTS_CREATE);
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, root().getUsableSpace());
        return cursor;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        include(cursor, fileForId(documentId), documentId);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        File parent = fileForId(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException("不是目录：" + parentDocumentId);
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        File[] files = parent.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                if (isPrivateName(file.getName())) continue;
                try { include(cursor, canonicalUnderRoot(file), documentIdFor(file)); }
                catch (FileNotFoundException ignored) { }
            }
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, android.os.CancellationSignal signal) throws FileNotFoundException {
        File file = fileForId(documentId);
        if (!file.isFile()) throw new FileNotFoundException("不是文件：" + documentId);
        String requested = mode == null || mode.isEmpty() ? "r" : mode;
        int flags;
        if (requested.startsWith("rw")) flags = ParcelFileDescriptor.MODE_READ_WRITE;
        else if (requested.startsWith("w")) flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE;
        else if (requested.startsWith("r")) flags = ParcelFileDescriptor.MODE_READ_ONLY;
        else throw new FileNotFoundException("不支持的模式：" + mode);
        if (flags != ParcelFileDescriptor.MODE_READ_ONLY && requested.contains("t")) flags |= ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        File parent = fileForId(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException("不是目录：" + parentDocumentId);
        File target = canonicalUnderRoot(new File(parent, safeName(displayName)));
        if (target.exists()) throw new FileNotFoundException("同名条目已存在：" + target.getName());
        try {
            boolean created = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)
                ? target.mkdir() : target.createNewFile();
            if (!created) throw new IOException("无法创建 " + target.getName());
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        return documentIdFor(target);
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = fileForId(documentId);
        if (file.equals(root())) throw new FileNotFoundException("无法删除共享根目录");
        deleteTree(file);
    }

    @Override public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = fileForId(documentId);
        File parent = file.getParentFile();
        if (parent == null) throw new FileNotFoundException("无法重命名共享根目录");
        File target = canonicalUnderRoot(new File(parent, safeName(displayName)));
        if (target.equals(file)) return documentId;
        if (target.exists()) throw new FileNotFoundException("同名条目已存在：" + target.getName());
        if (!file.renameTo(target)) throw new FileNotFoundException("无法重命名：" + file.getName());
        return documentIdFor(target);
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            File parent = fileForId(parentDocumentId);
            File child = fileForId(documentId);
            return child.getPath().startsWith(parent.getPath() + File.separator) || child.equals(parent);
        } catch (Exception ignored) { return false; }
    }

    private static String[] documentColumns(String[] projection) {
        return projection == null ? new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
        } : projection;
    }

    private void include(MatrixCursor cursor, File file, String id) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ROOT_DOCUMENT_ID.equals(id) ? "HOME" : file.getName());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : mime(file));
        row.add(DocumentsContract.Document.COLUMN_FLAGS, file.isDirectory()
            ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            : DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME);
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.isFile() ? file.length() : null);
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    private static String mime(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1));
        return type == null ? "application/octet-stream" : type;
    }

    /** Agent-private state: never listed, opened, created or dumped into by external apps. */
    private static boolean isPrivateName(String name) {
        return PRIVATE_DIR_IQ.equals(name) || PRIVATE_DIR_TERMUX.equals(name);
    }

    private static String safeName(String displayName) throws FileNotFoundException {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new FileNotFoundException("非法名称：" + displayName);
        }
        if (isPrivateName(name)) throw new FileNotFoundException("受保护目录：" + name);
        return name;
    }

    private static void deleteTree(File file) throws FileNotFoundException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new FileNotFoundException("无法删除：" + file.getName());
    }

    private static File root() {
        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        if (!home.isDirectory()) home.mkdirs();
        return home;
    }

    private static String documentIdFor(File file) throws FileNotFoundException {
        File base = canonicalUnderRoot(root());
        File target = canonicalUnderRoot(file);
        if (target.equals(base)) return ROOT_DOCUMENT_ID;
        String relative = target.getPath().substring(base.getPath().length());
        while (relative.startsWith(File.separator)) relative = relative.substring(1);
        return ROOT_DOCUMENT_ID + "/" + relative.replace(File.separatorChar, '/');
    }

    private static File fileForId(String id) throws FileNotFoundException {
        if (id == null || !id.equals(ROOT_DOCUMENT_ID) && !id.startsWith(ROOT_DOCUMENT_ID + "/")) throw new FileNotFoundException("无效文档 ID");
        String relative = id.equals(ROOT_DOCUMENT_ID) ? "" : id.substring(ROOT_DOCUMENT_ID.length() + 1);
        if (relative.indexOf("..") >= 0) throw new FileNotFoundException("非法路径：" + id);
        for (String part : relative.split("/")) if (isPrivateName(part)) throw new FileNotFoundException("受保护目录：" + part);
        return canonicalUnderRoot(new File(root(), relative));
    }

    private static File canonicalUnderRoot(File candidate) throws FileNotFoundException {
        try {
            File base = root().getCanonicalFile();
            File file = candidate.getCanonicalFile();
            if (!file.equals(base) && !file.getPath().startsWith(base.getPath() + File.separator)) throw new FileNotFoundException("路径越界");
            return file;
        } catch (FileNotFoundException e) { throw e; }
        catch (Exception e) { throw new FileNotFoundException(e.getMessage()); }
    }
}
