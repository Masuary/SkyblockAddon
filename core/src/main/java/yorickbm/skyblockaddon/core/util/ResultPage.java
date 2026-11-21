package yorickbm.skyblockaddon.core.util;

import java.util.List;

public record ResultPage<T>(List<T> entries, int pageNumber, int pageCount, int totalEntries) {

    public static <T> ResultPage<T> of(final List<T> allEntries, final int requestedPageNumber, final int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive, got " + pageSize);
        }
        final int totalEntries = allEntries.size();
        final int pageCount = Math.max(1, (totalEntries + pageSize - 1) / pageSize);
        final int pageNumber = Math.min(Math.max(requestedPageNumber, 1), pageCount);
        final int firstIndex = (pageNumber - 1) * pageSize;
        final int endIndex = Math.min(firstIndex + pageSize, totalEntries);
        return new ResultPage<>(List.copyOf(allEntries.subList(firstIndex, endIndex)), pageNumber, pageCount, totalEntries);
    }
}
