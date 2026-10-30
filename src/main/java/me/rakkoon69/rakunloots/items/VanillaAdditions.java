package me.rakkoon69.rakunloots.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Item data written on a vanilla item reference. Applied only for the vanilla source.
 * A missing field leaves that part of the item. An empty name, lore, enchants, or flags list clears it.
 */
public final class VanillaAdditions {

    private final Optional<String> name;
    private final Optional<List<String>> lore;
    private final Optional<List<String>> enchants;
    private final Optional<List<String>> flags;
    private final Optional<Object> customModelData;
    private final Optional<Object> unbreakable;
    private final Optional<Object> damage;
    private final Optional<Map<String, Object>> nbt;

    private VanillaAdditions(Builder builder) {
        this.name = builder.name;
        this.lore = builder.lore.map(List::copyOf);
        this.enchants = builder.enchants.map(List::copyOf);
        this.flags = builder.flags.map(List::copyOf);
        this.customModelData = builder.customModelData;
        this.unbreakable = builder.unbreakable;
        this.damage = builder.damage;
        this.nbt = builder.nbt.map(m -> Collections.unmodifiableMap(new LinkedHashMap<>(m)));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VanillaAdditions none() {
        return builder().build();
    }

    public Optional<String> name() {
        return name;
    }

    public Optional<List<String>> lore() {
        return lore;
    }

    public Optional<List<String>> enchants() {
        return enchants;
    }

    public Optional<List<String>> flags() {
        return flags;
    }

    public Optional<Object> customModelData() {
        return customModelData;
    }

    public Optional<Object> unbreakable() {
        return unbreakable;
    }

    public Optional<Object> damage() {
        return damage;
    }

    public Optional<Map<String, Object>> nbt() {
        return nbt;
    }

    public boolean isEmpty() {
        return name.isEmpty()
                && lore.isEmpty()
                && enchants.isEmpty()
                && flags.isEmpty()
                && customModelData.isEmpty()
                && unbreakable.isEmpty()
                && damage.isEmpty()
                && nbt.isEmpty();
    }

    public static final class Builder {
        private Optional<String> name = Optional.empty();
        private Optional<List<String>> lore = Optional.empty();
        private Optional<List<String>> enchants = Optional.empty();
        private Optional<List<String>> flags = Optional.empty();
        private Optional<Object> customModelData = Optional.empty();
        private Optional<Object> unbreakable = Optional.empty();
        private Optional<Object> damage = Optional.empty();
        private Optional<Map<String, Object>> nbt = Optional.empty();

        public Builder name(String name) {
            this.name = Optional.of(name == null ? "" : name);
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = Optional.of(lore == null ? List.of() : new ArrayList<>(lore));
            return this;
        }

        public Builder enchants(List<String> enchants) {
            this.enchants = Optional.of(enchants == null ? List.of() : new ArrayList<>(enchants));
            return this;
        }

        public Builder flags(List<String> flags) {
            this.flags = Optional.of(flags == null ? List.of() : new ArrayList<>(flags));
            return this;
        }

        public Builder customModelData(Object customModelData) {
            this.customModelData = Optional.ofNullable(customModelData);
            return this;
        }

        public Builder unbreakable(Object unbreakable) {
            this.unbreakable = Optional.ofNullable(unbreakable);
            return this;
        }

        public Builder damage(Object damage) {
            this.damage = Optional.ofNullable(damage);
            return this;
        }

        public Builder nbt(Map<String, Object> nbt) {
            this.nbt = Optional.of(nbt == null ? Map.of() : new LinkedHashMap<>(nbt));
            return this;
        }

        public VanillaAdditions build() {
            return new VanillaAdditions(this);
        }
    }
}
