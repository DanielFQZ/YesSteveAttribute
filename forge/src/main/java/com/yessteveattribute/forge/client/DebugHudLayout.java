package com.yessteveattribute.forge.client;

/** Screen-bounded columns/pages; independent of Minecraft rendering for layout tests. */
public record DebugHudLayout(int x, int y, int width, int height, int rows, int columns,
                             int columnWidth, int page, int pages, int start, int end) {
    public static DebugHudLayout calculate(int screenWidth, int screenHeight, int count, int preferredWidth, int requestedPage) {
        int availableWidth = Math.max(1, Math.min(screenWidth - 12, (int) (screenWidth * .65)));
        int rows = Math.max(1, (screenHeight - 50) / 11);
        int columnWidth = Math.min(availableWidth, Math.max(160, preferredWidth));
        int columns = Math.max(1, Math.min((Math.max(1, count) + rows - 1) / rows, availableWidth / columnWidth));
        int capacity = rows * columns;
        int pages = Math.max(1, (count + capacity - 1) / capacity);
        int page = Math.floorMod(requestedPage, pages);
        int start = page * capacity;
        int end = Math.min(count, start + capacity);
        int usedRows = Math.min(rows, Math.max(1, end - start));
        int width = columnWidth * columns;
        int height = usedRows * 11 + 30;
        return new DebugHudLayout(Math.max(0, screenWidth - width - 6), 8, width, height,
                rows, columns, columnWidth, page, pages, start, end);
    }
}
