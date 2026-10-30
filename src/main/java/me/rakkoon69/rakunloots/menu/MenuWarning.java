package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Objects;
import java.util.Optional;

/**
 * A menu problem raised while drawing: today, a layout item that its source cannot resolve.
 */
public record MenuWarning(Kind kind, String menuId, String entry, ItemReference item) implements Warning {

    public MenuWarning {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(item, "item");
    }

    public enum Kind implements WarningKind {
        MENU_LAYOUT_ITEM_MISSING(Severity.HIGH);

        private final Severity defaultSeverity;

        Kind(Severity defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    public static MenuWarning layoutItemMissing(String menuId, String entry, ItemReference item) {
        return new MenuWarning(Kind.MENU_LAYOUT_ITEM_MISSING, menuId, entry, item);
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Optional<String> subject() {
        return Optional.of(menuId);
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("menu", Optional.of(menuId)),
                Warning.field("entry", Optional.of(entry)),
                Warning.field("source", Optional.ofNullable(item.source())),
                Warning.field("item", Optional.of(item.id()))
        );
    }

    @Override
    public Optional<Throwable> cause() {
        return Optional.empty();
    }
}
