package media.whitewhale.iduo.test.widgetfixture;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import media.whitewhale.iduo.test.R;

abstract class FixtureWidgetProvider extends AppWidgetProvider {
    static void updateFixture(Context context, AppWidgetManager manager, int id, boolean configured) {
        String preferenceKey = "configured-" + id;
        if (configured) {
            context.getSharedPreferences("widget-fixture", Context.MODE_PRIVATE)
                    .edit().putBoolean(preferenceKey, true).apply();
        }
        boolean isConfigured = configured || context
                .getSharedPreferences("widget-fixture", Context.MODE_PRIVATE)
                .getBoolean(preferenceKey, false);
        Bundle options = manager.getAppWidgetOptions(id);
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1);
        String prefix = isConfigured ? "Configured fixture is live" : "Fixture widget is live";
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_fixture_live);
        views.setTextViewText(R.id.widget_fixture_text,
                prefix + " \u00b7 " + width + " \u00d7 " + height + " dp");
        manager.updateAppWidget(id, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateFixture(context, manager, id, false);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int id, Bundle options) {
        updateFixture(context, manager, id, false);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        android.content.SharedPreferences.Editor editor = context
                .getSharedPreferences("widget-fixture", Context.MODE_PRIVATE).edit();
        for (int id : ids) editor.remove("configured-" + id);
        editor.apply();
    }
}
