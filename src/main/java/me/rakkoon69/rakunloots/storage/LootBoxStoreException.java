package me.rakkoon69.rakunloots.storage;

/**
 * Storage could not be reached or did not accept a read or write.
 */
public final class LootBoxStoreException extends Exception {

    public LootBoxStoreException(String message) {
        super(message);
    }

    public LootBoxStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
