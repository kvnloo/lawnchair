package app.lawnchair.zer0.voice.z0live;

import android.app.PendingIntent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.android.launcher3.R;

public final class Z0LiveTile extends TileService implements Z0LiveStatus.Observer {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Z0LiveStatus.observe(this);
        onChanged(Z0LiveStatus.get());
    }

    @Override
    public void onStopListening() {
        Z0LiveStatus.remove(this);
        super.onStopListening();
    }

    @Override
    public void onChanged(Z0LiveStatus.Snapshot snapshot) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel(getString(R.string.z0live_title));
        tile.setState(snapshot.active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    // Intent overload is required below API 34; the PendingIntent API does not exist there.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();
        Runnable open = () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 2,
                        Z0LiveActivity.intent(this, true),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(Z0LiveActivity.intent(this, true));
            }
        };
        if (isLocked()) unlockAndRun(open);
        else open.run();
    }
}
