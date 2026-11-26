package com.cheji.play;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Serves the downloaded APK to the system installer without androidx FileProvider
 * and without a file:// URI (banned on API 24+).
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.cheji.play.apk";
    private static final int EXT = 1;
    private static final int INT = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "ext/*", EXT);
        MATCHER.addURI(AUTHORITY, "int/*", INT);
    }

    private File dir(int code) {
        android.content.Context c = getContext();
        if (c == null) {
            return null;
        }
        if (code == EXT) {
            return c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        }
        return new File(c.getFilesDir(), "download");
    }

    public static Uri uriFor(android.content.Context c, File apk) {
        File parent = apk.getParentFile();
        int code = INT;
        if (parent != null) {
            File ext = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File in = new File(c.getFilesDir(), "download");
            if (ext != null && ext.getAbsolutePath().equals(parent.getAbsolutePath())) {
                code = EXT;
            } else if (!in.getAbsolutePath().equals(parent.getAbsolutePath())) {
                code = EXT;
            }
        }
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(code == EXT ? "ext" : "int").appendPath(apk.getName()).build();
    }

    private File resolve(Uri uri) {
        int code = MATCHER.match(uri);
        if (code != EXT && code != INT) {
            return null;
        }
        List<String> segs = uri.getPathSegments();
        if (segs == null || segs.size() != 2) {
            return null;
        }
        String name = segs.get(1);
        if (!name.endsWith(".apk") || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        File base = dir(code);
        if (base == null) {
            return null;
        }
        File f = new File(base, name);
        return f.isFile() && f.getAbsolutePath().startsWith(base.getAbsolutePath()) ? f : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) && !"rb".equals(mode) && !"wt".equals(mode)) {
            throw new FileNotFoundException("唯讀提供 APK");
        }
        File f = resolve(uri);
        if (f == null) {
            throw new FileNotFoundException("找不到這個 APK");
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return resolve(uri) == null ? null : "application/vnd.android.package-archive";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
