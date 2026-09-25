package dev.applingo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile that opens AppLingo. */
public final class AppTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) { tile.setState(Tile.STATE_ACTIVE); tile.updateTile(); }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override public void onClick() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
