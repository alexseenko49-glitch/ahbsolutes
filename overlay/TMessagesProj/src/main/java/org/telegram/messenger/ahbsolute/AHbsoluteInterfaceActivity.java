package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * "Изменение интерфейса": reorder the buttons of the main screen (side menu).
 * Chats are intentionally not affected.
 */
public class AHbsoluteInterfaceActivity extends BaseFragment {

    private LinearLayout content;
    private final ArrayList<Integer> order = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(t("Изменение интерфейса", "Interface editor"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frame;

        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        loadOrder();
        rebuild(context);
        return fragmentView;
    }

    private void loadOrder() {
        order.clear();
        int[] ids = AHbsolute.getDrawerIds();
        ArrayList<Integer> known = new ArrayList<>();
        for (int id : ids) known.add(id);
        for (int id : AHbsolute.getDrawerOrder()) if (known.contains(id) && !order.contains(id)) order.add(id);
        for (int id : ids) if (!order.contains(id)) order.add(id);
    }

    private String labelOf(int id) {
        int[] ids = AHbsolute.getDrawerIds();
        String[] labels = AHbsolute.getDrawerLabels();
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return labels[i];
        return String.valueOf(id);
    }

    private void save() {
        int[] arr = new int[order.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = order.get(i);
        AHbsolute.setDrawerOrder(arr);
    }

    private TextView arrow(Context c, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        tv.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        return tv;
    }

    private void rebuild(Context c) {
        content.removeAllViews();

        TextView hint = new TextView(c);
        hint.setText(t("Переставьте кнопки главного экрана (боковое меню). В чатах интерфейс не меняется.",
                "Reorder the main-screen buttons (side menu). Chats are not affected."));
        hint.setTextSize(13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        content.addView(hint);

        if (order.isEmpty()) {
            TextView empty = new TextView(c);
            empty.setText(t("Список кнопок пока пуст. Войдите в аккаунт и откройте боковое меню один раз.",
                    "No buttons yet. Sign in and open the side menu once."));
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            empty.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(8), 0);

            TextView label = new TextView(c);
            label.setText(labelOf(order.get(i)));
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView up = arrow(c, "▲");
            up.setOnClickListener(v -> { if (idx > 0) { Collections.swap(order, idx, idx - 1); save(); rebuild(c); } });
            TextView down = arrow(c, "▼");
            down.setOnClickListener(v -> { if (idx < order.size() - 1) { Collections.swap(order, idx, idx + 1); save(); rebuild(c); } });
            row.addView(up);
            row.addView(down);
            content.addView(row, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        }

        TextView reset = new TextView(c);
        reset.setText(t("Сбросить порядок", "Reset order"));
        reset.setTextSize(16);
        reset.setTextColor(0xFFE53935);
        reset.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        reset.setOnClickListener(v -> { AHbsolute.setDrawerOrder(new int[0]); order.clear(); for (int id : AHbsolute.getDrawerIds()) order.add(id); rebuild(c); });
        content.addView(reset);
    }
}
