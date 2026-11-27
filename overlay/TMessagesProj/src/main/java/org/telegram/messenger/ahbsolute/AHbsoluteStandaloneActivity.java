package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Second launcher icon "AHbsolute ⚙": the AHbsolute settings (banners, avatar shape, interface, themes)
 * with Telegram's own action bar colours and list cells, so it looks like a regular Telegram settings page.
 */
public class AHbsoluteStandaloneActivity extends Activity implements AHbsoluteSettingsUi.Host {

    private AHbsoluteCardList content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { AHbsoluteUi.init(this); } catch (Throwable ignored) { }
        try { Theme.createCommonResources(this); } catch (Throwable ignored) { }

        int barColor = safeColor(Theme.key_actionBarDefault, 0xFF527DA3);
        int barText = safeColor(Theme.key_actionBarDefaultTitle, 0xFFFFFFFF);
        int barIcon = safeColor(Theme.key_actionBarDefaultIcon, 0xFFFFFFFF);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- action bar, same metrics as Telegram's ActionBar (56dp, back arrow, 20sp medium title) ----
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(barColor);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_ab_back);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setColorFilter(new PorterDuffColorFilter(barIcon, PorterDuff.Mode.SRC_IN));
        try { back.setBackground(Theme.createSelectorDrawable(safeColor(Theme.key_actionBarDefaultSelector, 0x22FFFFFF), 1)); } catch (Throwable ignored) { }
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(AndroidUtilities.dp(54), AndroidUtilities.dp(56), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        TextView title = new TextView(this);
        title.setText("AHbsolute");
        title.setTextColor(barText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, AndroidUtilities.dp(56), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tlp.leftMargin = AndroidUtilities.dp(72);
        bar.addView(title, tlp);
        root.addView(bar, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(56)));

        FrameLayout backdrop = AHbsoluteSettingsUi.backdrop(this);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = new AHbsoluteCardList(this);
        sv.addView(content, new FrameLayout.LayoutParams(-1, -2));
        backdrop.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(backdrop, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        try {
            Window w = getWindow();
            w.setStatusBarColor(barColor);
            boolean light = AndroidUtilities.computePerceivedBrightness(barColor) > 0.721f;
            if (Build.VERSION.SDK_INT >= 23) {
                View d = w.getDecorView();
                int f = d.getSystemUiVisibility();
                d.setSystemUiVisibility(light ? f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Throwable ignored) { }

        rebuild();
    }

    private static int safeColor(int key, int def) {
        try { return Theme.getColor(key); } catch (Throwable e) { return def; }
    }

    @Override
    public void rebuild() {
        try {
            AHbsoluteSettingsUi.build(this, content, this);
        } catch (Throwable e) {
            content.removeAllViews();
            TextView tv = new TextView(this);
            tv.setText("AHbsolute: " + e);
            tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
            content.addView(tv);
        }
    }

    @Override
    public void pickBanner() {
        try {
            startActivityForResult(AHbsoluteSettingsUi.pickIntent(), AHbsoluteSettingsUi.REQ_BANNER);
        } catch (Throwable e) {
            Toast.makeText(this, String.valueOf(e), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public Runnable interfaceEditor() {
        return null; // the side-menu editor lives inside Telegram: Settings → AHbsolute
    }

    @Override
    public Runnable reposts() {
        return null; // the reposts list opens channels, which needs Telegram's own window
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        AHbsoluteSettingsUi.onPicked(this, requestCode, resultCode, data, this);
    }
}
