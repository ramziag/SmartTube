package com.liskovsoft.smartyoutubetv2.common.misc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Sign-in data that must never leave the app's private storage.<br/>
 * Backups are staged on shared storage (readable by any app holding the storage permission, at least up to Android 10)
 * and uploaded to Google Drive, so these files are left out of every backup.<br/>
 * Restores that wipe the files dir put the current ones back instead, so the user stays signed in.
 */
public final class BackupCredentials {
    /**
     * Paths relative to the app's files dir.<br/>
     * See sharedutils GlobalPreferences: PREFS_DIR + MEDIA_SERVICE_ACCOUNT_DATA / OAUTH2_ACCOUNT_DATA.
     */
    private static final String[] FILES = {
            "global_prefs/media_service_account_data", // YouTube accounts, including their OAuth refresh tokens
            "global_prefs/oauth2_account_data" // Google Drive OAuth refresh token
    };

    private BackupCredentials() {
    }

    /**
     * Whether the file (located inside the app's files dir) holds sign-in data.
     */
    public static boolean isCredential(File filesDir, File file) {
        if (filesDir == null || file == null) {
            return false;
        }

        String path = file.getAbsolutePath();

        for (String name : FILES) {
            if (new File(filesDir, name).getAbsolutePath().equals(path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Reads the current sign-in data so it can survive a restore that wipes the files dir.
     */
    public static Map<String, byte[]> snapshot(File filesDir) {
        Map<String, byte[]> result = new HashMap<>();

        if (filesDir == null) {
            return result;
        }

        for (String name : FILES) {
            File file = new File(filesDir, name);

            if (!file.isFile()) {
                continue;
            }

            try {
                result.put(name, readBytes(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    /**
     * Puts back the sign-in data that the restored backup didn't contain.<br/>
     * Backups made before credentials were excluded may still contain them. Those are kept as before.
     */
    public static void restoreMissing(File filesDir, Map<String, byte[]> snapshot) {
        if (filesDir == null || snapshot == null) {
            return;
        }

        for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
            File file = new File(filesDir, entry.getKey());

            if (file.exists()) {
                continue;
            }

            File parent = file.getParentFile();

            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                continue;
            }

            try {
                writeBytes(file, entry.getValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }
}
