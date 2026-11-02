package me.rakkoon69.rakunloots.server.bukkit;

import be.seeseemelk.mockbukkit.profile.PlayerProfileMock;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.UUID;

/**
 * A MockBukkit profile with working textures, which MockBukkit 1.18 leaves unimplemented. It refuses what
 * CraftPlayerProfile refuses: no UUID and a blank name, or a name longer than 16 characters.
 */
final class FakePlayerProfile extends PlayerProfileMock {

    private final FakeTextures textures = new FakeTextures();

    FakePlayerProfile(@Nullable UUID uniqueId, @Nullable String name) {
        super(name, uniqueId);
        if (uniqueId == null && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("uniqueId is null and name is blank");
        }
        if (name != null && name.length() > 16) {
            throw new IllegalArgumentException("The name of the profile is longer than 16 characters");
        }
    }

    @Override
    public @NotNull PlayerTextures getTextures() {
        return textures;
    }

    /**
     * Textures holding a skin only. The skull fake reads nothing else.
     */
    private static final class FakeTextures implements PlayerTextures {

        private URL skin;

        @Override
        public boolean isEmpty() {
            return skin == null;
        }

        @Override
        public void clear() {
            skin = null;
        }

        @Override
        public @Nullable URL getSkin() {
            return skin;
        }

        @Override
        public void setSkin(@Nullable URL skinUrl) {
            skin = skinUrl;
        }

        @Override
        public void setSkin(@Nullable URL skinUrl, @Nullable SkinModel skinModel) {
            skin = skinUrl;
        }

        @Override
        public @NotNull SkinModel getSkinModel() {
            return SkinModel.CLASSIC;
        }

        @Override
        public @Nullable URL getCape() {
            return null;
        }

        @Override
        public void setCape(@Nullable URL capeUrl) {
            throw new UnsupportedOperationException("not faked");
        }

        @Override
        public long getTimestamp() {
            throw new UnsupportedOperationException("not faked");
        }

        @Override
        public boolean isSigned() {
            return false;
        }
    }
}
