package app.lawnchair.zer0.voice.z0live;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.android.launcher3.R;

public final class Z0LiveWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.z0live_widget);
            views.setOnClickPendingIntent(R.id.z0live_widget_voice, PendingIntent.getActivity(context, 0,
                    Z0LiveActivity.intent(context, true),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            views.setOnClickPendingIntent(R.id.z0live_widget_ripple, PendingIntent.getActivity(context, 1,
                    new Intent(context, RippleActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
    }
}
