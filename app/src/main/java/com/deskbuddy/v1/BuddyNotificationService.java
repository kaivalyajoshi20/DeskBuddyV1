package com.deskbuddy.v1;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public class BuddyNotificationService extends NotificationListenerService {
    private BuddyBleManager ble;
    private MediaSessionManager mediaSessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        ble = BuddyBleManager.get(this);
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        publishMedia();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publishMedia();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publishMedia();
    }

    private void publishMedia() {
        if (mediaSessionManager == null) return;
        try {
            List<MediaController> controllers =
                    mediaSessionManager.getActiveSessions(
                            new ComponentName(this, BuddyNotificationService.class));

            if (controllers == null || controllers.isEmpty()) return;

            MediaController best = controllers.get(0);
            MediaMetadata md = best.getMetadata();
            if (md == null) return;

            String title = safe(md.getString(MediaMetadata.METADATA_KEY_TITLE));
            String artist = safe(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
            String app = best.getPackageName();
            boolean playing = best.getPlaybackState() != null &&
                    best.getPlaybackState().getState() ==
                            android.media.session.PlaybackState.STATE_PLAYING;

            String json = "{\"type\":\"MUSIC\",\"title\":\"" + esc(title) +
                    "\",\"artist\":\"" + esc(artist) +
                    "\",\"app\":\"" + esc(app) +
                    "\",\"playing\":" + playing + "}";

            ble.sendJson(json);
        } catch (SecurityException ignored) {
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
