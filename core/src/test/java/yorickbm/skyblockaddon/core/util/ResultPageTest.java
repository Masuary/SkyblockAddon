package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultPageTest {
    private static final List<Integer> TWENTY_FIVE_ENTRIES = IntStream.rangeClosed(1, 25).boxed().toList();

    @Test
    void emptyListHasOneEmptyPage() {
        final ResultPage<Integer> page = ResultPage.of(List.of(), 1, 10);

        assertEquals(List.of(), page.entries());
        assertEquals(1, page.pageNumber());
        assertEquals(1, page.pageCount());
        assertEquals(0, page.totalEntries());
    }

    @Test
    void lastPageHoldsTheRemainder() {
        final ResultPage<Integer> page = ResultPage.of(TWENTY_FIVE_ENTRIES, 3, 10);

        assertEquals(List.of(21, 22, 23, 24, 25), page.entries());
        assertEquals(3, page.pageNumber());
        assertEquals(3, page.pageCount());
        assertEquals(25, page.totalEntries());
    }

    @Test
    void middlePageHoldsAFullPage() {
        final ResultPage<Integer> page = ResultPage.of(TWENTY_FIVE_ENTRIES, 2, 10);

        assertEquals(IntStream.rangeClosed(11, 20).boxed().toList(), page.entries());
    }

    @Test
    void pageBeyondTheEndShowsTheLastPage() {
        final ResultPage<Integer> page = ResultPage.of(TWENTY_FIVE_ENTRIES, 9, 10);

        assertEquals(3, page.pageNumber());
        assertEquals(List.of(21, 22, 23, 24, 25), page.entries());
    }

    @Test
    void pageBelowOneShowsTheFirstPage() {
        final ResultPage<Integer> page = ResultPage.of(TWENTY_FIVE_ENTRIES, 0, 10);

        assertEquals(1, page.pageNumber());
        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(), page.entries());
    }

    @Test
    void nonPositivePageSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ResultPage.of(TWENTY_FIVE_ENTRIES, 1, 0));
    }
}
