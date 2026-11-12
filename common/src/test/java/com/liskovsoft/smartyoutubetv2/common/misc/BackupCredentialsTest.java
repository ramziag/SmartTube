package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupCredentialsTest {
    private static final String ACCOUNTS = "global_prefs/media_service_account_data";
    private static final String DRIVE = "global_prefs/oauth2_account_data";
    private static final String SETTINGS = "global_prefs/media_service_data";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private File mFilesDir;

    @Before
    public void setUp() throws IOException {
        mFilesDir = mTemp.newFolder("files");
    }

    @Test
    public void isCredential_matchesOnlySignInFiles() {
        assertTrue(BackupCredentials.isCredential(mFilesDir, new File(mFilesDir, ACCOUNTS)));
        assertTrue(BackupCredentials.isCredential(mFilesDir, new File(mFilesDir, DRIVE)));

        assertFalse(BackupCredentials.isCredential(mFilesDir, new File(mFilesDir, SETTINGS)));
        assertFalse(BackupCredentials.isCredential(mFilesDir, new File(mFilesDir, "app_prefs/media_service_account_data")));
        assertFalse(BackupCredentials.isCredential(mFilesDir, new File(mFilesDir, "global_prefs")));
        assertFalse(BackupCredentials.isCredential(null, new File(mFilesDir, ACCOUNTS)));
        assertFalse(BackupCredentials.isCredential(mFilesDir, null));
    }

    @Test
    public void isCredential_matchesChildrenBuiltDuringRecursiveCopy() {
        // FileHelpers.copy walks the tree with new File(parent, childName)
        File globalPrefs = new File(mFilesDir, "global_prefs");

        assertTrue(BackupCredentials.isCredential(mFilesDir, new File(globalPrefs, "media_service_account_data")));
        assertTrue(BackupCredentials.isCredential(mFilesDir, new File(globalPrefs, "oauth2_account_data")));
    }

    @Test
    public void credentialsSurviveRestoreThatWipesFilesDir() throws IOException {
        write(ACCOUNTS, "refresh-token-youtube");
        write(DRIVE, "refresh-token-drive");
        write(SETTINGS, "old-settings");

        Map<String, byte[]> snapshot = BackupCredentials.snapshot(mFilesDir);
        assertEquals(2, snapshot.size());

        // Simulate GDriveBackupManager: wipe the files dir, then unpack a backup without credentials
        deleteRecursively(mFilesDir);
        write(SETTINGS, "restored-settings");

        BackupCredentials.restoreMissing(mFilesDir, snapshot);

        assertEquals("refresh-token-youtube", read(ACCOUNTS));
        assertEquals("refresh-token-drive", read(DRIVE));
        assertEquals("restored-settings", read(SETTINGS));
    }

    @Test
    public void restoreMissing_recreatesParentDir() throws IOException {
        write(ACCOUNTS, "refresh-token-youtube");
        Map<String, byte[]> snapshot = BackupCredentials.snapshot(mFilesDir);

        deleteRecursively(mFilesDir); // old-format restore: nothing restored into the files dir

        BackupCredentials.restoreMissing(mFilesDir, snapshot);

        assertEquals("refresh-token-youtube", read(ACCOUNTS));
    }

    @Test
    public void restoreMissing_keepsCredentialsFromOlderBackups() throws IOException {
        write(ACCOUNTS, "current-token");
        Map<String, byte[]> snapshot = BackupCredentials.snapshot(mFilesDir);

        deleteRecursively(mFilesDir);
        write(ACCOUNTS, "token-from-old-backup");

        BackupCredentials.restoreMissing(mFilesDir, snapshot);

        assertEquals("token-from-old-backup", read(ACCOUNTS));
    }

    @Test
    public void snapshot_preservesBinaryContent() throws IOException {
        byte[] data = new byte[20_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        File file = new File(mFilesDir, DRIVE);
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }

        Map<String, byte[]> snapshot = BackupCredentials.snapshot(mFilesDir);
        deleteRecursively(mFilesDir);
        BackupCredentials.restoreMissing(mFilesDir, snapshot);

        assertArrayEquals(data, readBytes(new File(mFilesDir, DRIVE)));
    }

    @Test
    public void signedOutUser_nothingToSnapshotOrRestore() {
        Map<String, byte[]> snapshot = BackupCredentials.snapshot(mFilesDir);
        assertTrue(snapshot.isEmpty());

        BackupCredentials.restoreMissing(mFilesDir, snapshot);
        assertFalse(new File(mFilesDir, ACCOUNTS).exists());

        assertTrue(BackupCredentials.snapshot(null).isEmpty());
        BackupCredentials.restoreMissing(null, snapshot); // no crash
    }

    private void write(String name, String content) throws IOException {
        File file = new File(mFilesDir, name);
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String read(String name) throws IOException {
        return new String(readBytes(new File(mFilesDir, name)), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
