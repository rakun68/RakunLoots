package me.rakkoon69.rakunloots.hologram.decent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What DhapiCalls does when a step after DHAPI created the hologram fails. The real DHAPI needs a running
 * DecentHolograms past its name check, so the steps and the removal are the test's own.
 */
class DhapiCallsTest {

    private static final String NAME = "rakunloots_aaaaaaaa-0000-0000-0000-000000000001";

    /**
     * A failing step removes the hologram just created, by its name, and the failure reaches the caller.
     */
    @Test
    void aFailedStepRemovesTheHologramAndRethrows() {
        List<String> removed = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("set lines failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> DhapiCalls.completeOrRemove(
                NAME, () -> {
                    throw failure;
                }, removed::add));

        assertSame(failure, thrown);
        assertEquals(List.of(NAME), removed);
    }

    /**
     * A removal that fails too does not hide the first failure.
     */
    @Test
    void aFailedRemovalDoesNotHideTheFailedStep() {
        IllegalStateException failure = new IllegalStateException("set lines failed");
        IllegalStateException removal = new IllegalStateException("remove failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> DhapiCalls.completeOrRemove(
                NAME, () -> {
                    throw failure;
                }, name -> {
                    throw removal;
                }));

        assertSame(failure, thrown);
        assertEquals(List.of(removal), List.of(thrown.getSuppressed()));
    }

    @Test
    void stepsThatSucceedRemoveNothing() {
        List<String> removed = new ArrayList<>();

        DhapiCalls.completeOrRemove(NAME, () -> {
        }, removed::add);

        assertEquals(List.of(), removed);
    }
}
