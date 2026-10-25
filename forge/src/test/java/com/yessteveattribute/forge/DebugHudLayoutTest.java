package com.yessteveattribute.forge;

import com.yessteveattribute.forge.client.DebugHudLayout;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class DebugHudLayoutTest {
    @Test void everyEntryIsAccessibleOnceAndAllPagesStayOnScreen() {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {853, 480}, {1920, 1080}}) {
            for (int count : new int[]{0, 1, 80, 300}) {
                var first = DebugHudLayout.calculate(size[0], size[1], count, 220, 0);
                var indices = new HashSet<Integer>();
                for (int page = 0; page < first.pages(); page++) {
                    var layout = DebugHudLayout.calculate(size[0], size[1], count, 220, page);
                    assertTrue(layout.x() >= 0);
                    assertTrue(layout.x() + layout.width() <= size[0]);
                    assertTrue(layout.y() + layout.height() <= size[1]);
                    for (int i = layout.start(); i < layout.end(); i++) assertTrue(indices.add(i));
                }
                assertEquals(count, indices.size());
                assertEquals(first.pages() - 1, DebugHudLayout.calculate(size[0], size[1], count, 220, -1).page());
                assertEquals(0, DebugHudLayout.calculate(size[0], size[1], count, 220, first.pages()).page());
            }
        }
    }
}
