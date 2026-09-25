package media.whitewhale.iduo.test.widgetfixture;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WidgetFixtureConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, resultIntent());

        TextView title = new TextView(this);
        title.setText("Configure fixture widget");
        title.setTextSize(22f);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        Button confirm = new Button(this);
        confirm.setText("Use configured widget");
        confirm.setContentDescription("Confirm fixture configuration");
        confirm.setOnClickListener(view -> {
            FixtureWidgetProvider.updateFixture(
                    this, AppWidgetManager.getInstance(this), widgetId, true);
            setResult(RESULT_OK, resultIntent());
            finish();
        });

        Button cancel = new Button(this);
        cancel.setText("Cancel fixture configuration");
        cancel.setContentDescription("Cancel fixture configuration");
        cancel.setOnClickListener(view -> finish());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(48, 48, 48, 48);
        content.addView(title);
        content.addView(confirm);
        content.addView(cancel);
        setContentView(content);
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }
}
