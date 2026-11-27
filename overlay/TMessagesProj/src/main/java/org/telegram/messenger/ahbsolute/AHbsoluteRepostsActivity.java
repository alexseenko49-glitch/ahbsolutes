package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/** Profile → "Репосты": the posts you reposted; tap opens the original post in its channel. */
public class AHbsoluteRepostsActivity extends BaseFragment {

    private AHbsoluteCardList list;
    private Context ctx;

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(AHbsoluteReposts.rowTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;
        ScrollView sv = new ScrollView(context);
        sv.setVerticalScrollBarEnabled(false);
        list = new AHbsoluteCardList(context);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        rebuild();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (list == null) return;
        Context c = ctx;
        list.removeAllViews();
        list.gap(12);
        ArrayList<AHbsoluteReposts.Item> items = AHbsoluteReposts.list();
        if (items.isEmpty()) {
            TextView empty = new TextView(c);
            empty.setText(t("Здесь будут ваши репосты.\n\nЗажмите пост в любом канале и нажмите «Репост».",
                    "Your reposts will appear here.\n\nHold a post in any channel and tap \"Repost\"."));
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(40), AndroidUtilities.dp(24), AndroidUtilities.dp(40));
            list.addCard(empty, -2);
            return;
        }
        HeaderCell h = new HeaderCell(c);
        h.setText(t("Мои репосты", "My reposts") + " · " + items.size());
        list.addCard(h, -2);
        for (int i = 0; i < items.size(); i++) {
            final AHbsoluteReposts.Item it = items.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(12));
            row.setBackground(Theme.getSelectorDrawable(false));
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView title = new TextView(c);
            title.setText(TextUtils.isEmpty(it.title) ? t("Канал", "Channel") : it.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView date = new TextView(c);
            try { date.setText(it.date > 0 ? LocaleController.formatDateChat(it.date) : ""); } catch (Throwable e) { date.setText(""); }
            date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            date.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            top.addView(date);
            row.addView(top);
            TextView text = new TextView(c);
            text.setText(it.text);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text.setMaxLines(3);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            text.setPadding(0, AndroidUtilities.dp(3), 0, 0);
            row.addView(text);
            row.setOnClickListener(v -> open(it));
            row.setOnLongClickListener(v -> {
                try {
                    new AlertDialog.Builder(c)
                            .setTitle(t("Убрать репост?", "Remove repost?"))
                            .setMessage(t("Пост исчезнет из вашего профиля. Сам пост в канале останется.", "The post disappears from your profile. The post itself stays in the channel."))
                            .setPositiveButton(t("Убрать", "Remove"), (d, w) -> { AHbsoluteReposts.remove(it.dialogId, it.messageId); rebuild(); })
                            .setNegativeButton(t("Отмена", "Cancel"), null)
                            .show();
                } catch (Throwable e) {
                    AHbsoluteReposts.remove(it.dialogId, it.messageId);
                    rebuild();
                }
                return true;
            });
            list.addCard(row, -2);
        }
        TextInfoPrivacyCell info = new TextInfoPrivacyCell(c);
        info.setText(t("Нажмите — откроется пост в канале. Удерживайте — убрать из репостов. Репосты видны в вашем профиле в AHbsolute.",
                "Tap to open the post in its channel. Hold to remove. Reposts are shown on your profile in AHbsolute."));
        list.addPlain(info);
    }

    private void open(AHbsoluteReposts.Item it) {
        try {
            Bundle args = new Bundle();
            if (it.dialogId < 0) args.putLong("chat_id", -it.dialogId); else args.putLong("user_id", it.dialogId);
            args.putInt("message_id", it.messageId);
            presentFragment(new ChatActivity(args));
        } catch (Throwable e) {
            android.widget.Toast.makeText(ctx, String.valueOf(e), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
