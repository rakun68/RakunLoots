package me.rakkoon69.rakunloots.server.bukkit;

import be.seeseemelk.mockbukkit.inventory.ItemFactoryMock;
import be.seeseemelk.mockbukkit.inventory.meta.SkullMetaMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A MockBukkit skull meta that holds an owner profile, which MockBukkit 1.18 leaves unimplemented. It refuses a
 * profile with neither a name nor textures, as CraftMetaSkull does.
 */
final class FakeSkullMeta extends SkullMetaMock {

    private PlayerProfile ownerProfile;

    FakeSkullMeta() {
    }

    private FakeSkullMeta(FakeSkullMeta meta) {
        super(meta);
        this.ownerProfile = meta.ownerProfile;
    }

    /**
     * An item factory that makes this meta for a player head, and keeps it when a stack takes it.
     */
    static ItemFactory itemFactory() {
        return new ItemFactoryMock() {
            @Override
            public ItemMeta getItemMeta(@NotNull Material material) {
                return material == Material.PLAYER_HEAD ? new FakeSkullMeta() : super.getItemMeta(material);
            }

            @Override
            public ItemMeta asMetaFor(@NotNull ItemMeta meta, @NotNull Material material) {
                return meta instanceof FakeSkullMeta skull && material == Material.PLAYER_HEAD ? skull.clone()
                        : super.asMetaFor(meta, material);
            }
        };
    }

    @Override
    public @Nullable PlayerProfile getOwnerProfile() {
        return ownerProfile;
    }

    @Override
    public void setOwnerProfile(@Nullable PlayerProfile profile) {
        if (profile != null && profile.getName() == null && profile.getTextures().isEmpty()) {
            throw new IllegalArgumentException("The skull profile is missing a name or textures!");
        }
        ownerProfile = profile;
    }

    @Override
    public FakeSkullMeta clone() {
        return new FakeSkullMeta(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FakeSkullMeta skull && super.equals(other)
                && Objects.equals(ownerProfile, skull.ownerProfile);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(ownerProfile);
    }
}
