package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Settings → AHbsolute (and the side menu entry). Same content as the "AHbsolute ⚙" launcher icon. */
public class AHbsoluteSettingsActivity extends BaseFragment implements AHbsoluteSettingsUi.Host {

    private LinearLayout content;
    private Context ctx;

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("AHbsolute");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        rebuild();
        return fragmentView;
    }

    @Override
    public void rebuild() {
        if (content != null && ctx != null) AHbsoluteSettingsUi.build(ctx, content, this);
    }

    @Override
    public void pickBanner() {
        try {
            startActivityForResult(AHbsoluteSettingsUi.pickIntent(), AHbsoluteSettingsUi.REQ_BANNER);
        } catch (Throwable e) {
            android.widget.Toast.makeText(ctx, String.valueOf(e), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public Runnable interfaceEditor() {
        // the editor reorders the old side menu; current Telegram versions have none
        if (AHbsolute.getDrawerIds().length == 0) return null;
        return () -> presentFragment(new AHbsoluteInterfaceActivity());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (ctx != null) AHbsoluteSettingsUi.onPicked(ctx, requestCode, resultCode, data, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }
}
