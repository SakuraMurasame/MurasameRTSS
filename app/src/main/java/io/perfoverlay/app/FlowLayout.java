package io.perfoverlay.app;

import java.util.ArrayList;
import java.util.List;

/** Wraps whole metrics without shrinking the user's chosen font. */
public final class FlowLayout {
    public static final class Item {
        public final int index, row;
        public final float x, width;
        Item(int index, int row, float x, float width) {
            this.index = index; this.row = row; this.x = x; this.width = width;
        }
    }
    public final List<Item> items = new ArrayList<>();
    public float width;
    public int rows;
    public static FlowLayout compute(float[] widths, float maxWidth, float gap, float padding, boolean vertical) {
        FlowLayout result = new FlowLayout();
        float available = Math.max(1, maxWidth - 2 * padding), x = 0;
        int row = 0;
        for (int i = 0; i < widths.length; i++) {
            float w = Math.min(available, Math.max(0, widths[i]));
            if (x > 0 && (vertical || x + gap + w > available)) { row++; x = 0; }
            if (x > 0) x += gap;
            result.items.add(new Item(i, row, padding + x, w));
            x += w;
            result.width = Math.max(result.width, x + padding * 2);
        }
        result.rows = widths.length == 0 ? 0 : row + 1;
        return result;
    }
}
