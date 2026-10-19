package app.lawnchair.zer0.voice.z0live;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.widget.RemoteViews;
import app.lawnchair.theme.color.tokens.ColorTokens;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public final class Z0LiveWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Context themed = new ContextThemeWrapper(context, Themes.getActivityThemeRes(context));
        int surface = ColorTokens.SurfaceContainerHighest.resolveColor(themed);
        int foreground = ColorTokens.TextColorPrimary.resolveColor(themed);
        int accent = ColorTokens.ColorAccent.resolveColor(themed);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.z0live_widget);
            views.setInt(R.id.z0live_widget_background, "setColorFilter", surface);
            views.setInt(R.id.z0live_widget_voice_icon, "setColorFilter", accent);
            views.setInt(R.id.z0live_widget_ripple_icon, "setColorFilter", accent);
            views.setTextColor(R.id.z0live_widget_voice_label, foreground);
            views.setTextColor(R.id.z0live_widget_ripple_label, foreground);
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
