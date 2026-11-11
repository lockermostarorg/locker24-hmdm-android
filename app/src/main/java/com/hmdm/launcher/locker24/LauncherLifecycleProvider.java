package com.hmdm.launcher.locker24;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Has no data. Android creates content providers before Application.onCreate and
 * before any activity, so this is the earliest point in our own code to register
 * {@link LauncherLifecycle} without touching the Application class or MainActivity.
 */
public class LauncherLifecycleProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        try {
            Context context = getContext();
            Context app = context != null ? context.getApplicationContext() : null;
            if (app instanceof Application) {
                LauncherLifecycle.ensureRegistered((Application) app);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
