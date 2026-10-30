package me.rakkoon69.rakunloots.server;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server probes with the Folia and Paper answers set by the test. The Folia adapter is loaded from the test's class
 * path, and each load is counted.
 */
final class FakeServerProbes implements ServerProbes {

    private final boolean folia;
    private final boolean paper;
    private final AtomicInteger foliaAdapterLoads = new AtomicInteger();

    FakeServerProbes(boolean folia, boolean paper) {
        this.folia = folia;
        this.paper = paper;
    }

    @Override
    public boolean folia() {
        return folia;
    }

    @Override
    public boolean paperChunkCall() {
        return paper;
    }

    @Override
    public Class<?> loadFoliaAdapter() throws ClassNotFoundException {
        foliaAdapterLoads.incrementAndGet();
        return Class.forName("me.rakkoon69.rakunloots.server.FoliaServerAdapter", true,
                FakeServerProbes.class.getClassLoader());
    }

    /**
     * How many times selection loaded the Folia adapter class.
     */
    int foliaAdapterLoads() {
        return foliaAdapterLoads.get();
    }
}
