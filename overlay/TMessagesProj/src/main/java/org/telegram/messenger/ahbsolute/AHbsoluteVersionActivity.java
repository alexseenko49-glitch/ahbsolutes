package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.BaseFragment;

/** The version easter egg inside Telegram's own navigation (back arrow / back gesture close it). */
public class AHbsoluteVersionActivity extends BaseFragment {
    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        fragmentView = AHbsoluteVersion.build(context, this::finishFragment);
        return fragmentView;
    }
}
