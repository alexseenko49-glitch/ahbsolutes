package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

/**
 * Vertical list that draws Telegram's rounded "sections": consecutive children added with {@link #card(View)}
 * share one rounded white card (12dp side padding, 16dp radius — the same metrics as RecyclerListView.setSections()).
 * Everything else (hints, gaps) sits directly on the grey screen background.
 * With {@link #glass} the cards become translucent frosted glass over a picture.
 */
public class AHbsoluteCardList extends LinearLayout {

    private static final Object CARD = new Object();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final HashMap<View, RectF> groups = new HashMap<>();
    public boolean glass;

    public AHbsoluteCardList(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setPadding(0, 0, 0, AndroidUtilities.dp(12));
    }

    /** marks a view as part of a card and gives it the side margins */
    public View card(View v) {
        v.setTag(CARD);
        return v;
    }

    public void addCard(View v, int height) {
        card(v);
        LayoutParams lp = new LayoutParams(-1, height);
        lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(12);
        addView(v, lp);
    }

    public void addPlain(View v) {
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(12);
        addView(v, lp);
    }

    /** starts a new card even if the previous view was a card too */
    public void gap(int dp) {
        View g = new View(getContext());
        addView(g, new LayoutParams(-1, AndroidUtilities.dp(dp)));
    }

    private static boolean isCard(View v) { return v.getTag() == CARD && v.getVisibility() == VISIBLE; }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        groups.clear();
        float r = AndroidUtilities.dp(16);
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        int n = getChildCount();
        int i = 0;
        while (i < n) {
            View first = getChildAt(i);
            if (!isCard(first)) { i++; continue; }
            int j = i;
            while (j + 1 < n && isCard(getChildAt(j + 1))) j++;
            View last = getChildAt(j);
            RectF g = new RectF(first.getLeft(), first.getTop(), first.getRight(), last.getBottom());
            for (int k = i; k <= j; k++) groups.put(getChildAt(k), g);
            if (glass) {
                AHbsoluteProfile.drawGlassSection(canvas, g, r, r, 1f, color);
            } else {
                paint.setColor(color);
                canvas.drawRoundRect(g, r, r, paint);
            }
            i = j + 1;
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        RectF g = groups.get(child);
        if (g == null) return super.drawChild(canvas, child, drawingTime);
        float r = AndroidUtilities.dp(16);
        clip.rewind();
        rect.set(g);
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        boolean res = super.drawChild(canvas, child, drawingTime);
        canvas.restore();
        return res;
    }
}
