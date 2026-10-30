package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.config.MenuButton;
import me.rakkoon69.rakunloots.config.MenuPage;
import me.rakkoon69.rakunloots.config.MenuSound;
import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.warning.Warnings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Turns one page of a menu file and its entries into a {@link MenuView}. Layout items resolve through the item
 * sources; a miss draws a barrier and warns once for that menu entry until the next reload.
 */
public final class MenuRenderer {

    private static final String PREVIOUS = "previous_page";
    private static final String NEXT = "next_page";
    private static final String MISSING_NAME_KEY = "menu_layout_item_missing_name";
    private static final String MISSING_LORE_KEY = "menu_layout_item_missing_lore";
    private static final NamespacedKey MISSING_ICON_KEY = key("rakunloots:missing_menu_icon");

    private final ItemSourceRegistry items;
    private final Function<String, String> language;
    private final Warnings warnings;
    private final Set<String> warned = new HashSet<>();

    public MenuRenderer(ItemSourceRegistry items, Function<String, String> language, Warnings warnings) {
        this.items = Objects.requireNonNull(items, "items");
        this.language = Objects.requireNonNull(language, "language");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Whether this stack is the barrier the renderer draws for a layout item that could not be resolved.
     */
    public static boolean isMissingIcon(ItemStack item) {
        if (item == null || item.getType() != Material.BARRIER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(MISSING_ICON_KEY, PersistentDataType.BYTE);
    }

    public int pageCount(MenuPage layout, int entryCount) {
        Objects.requireNonNull(layout, "layout");
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
        int slots = layout.contentSlots().size();
        if (entryCount == 0 || slots == 0) {
            return 1;
        }
        return (entryCount + slots - 1) / slots;
    }

    public int clampPage(MenuPage layout, int entryCount, int requestedPage) {
        int pages = pageCount(layout, entryCount);
        return requestedPage < 0 || requestedPage >= pages ? pages - 1 : requestedPage;
    }

    public MenuView render(String menuId, String title, MenuPage layout, List<MenuIcon> entries, int requestedPage,
                           IntConsumer goToPage) {
        return render(menuId, title, layout, entries, requestedPage, goToPage, language);
    }

    /**
     * Renders one page with the caller's captured language, so page labels use the same config snapshot as the rest
     * of the view. The constructor's language remains the default for the old overload.
     */
    public MenuView render(String menuId, String title, MenuPage layout, List<MenuIcon> entries, int requestedPage,
                           IntConsumer goToPage, Function<String, String> pageLanguage) {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(goToPage, "goToPage");
        Objects.requireNonNull(pageLanguage, "pageLanguage");
        List<MenuIcon> pageEntries = List.copyOf(entries);
        pageEntries.forEach(entry -> Objects.requireNonNull(entry, "entry"));

        int pages = pageCount(layout, pageEntries.size());
        int page = clampPage(layout, pageEntries.size(), requestedPage);
        MenuIcon filler = icon(menuId, "filler", layout.filler(), Optional.empty(), Optional.empty(), null, null,
                pages, page, pageLanguage);
        Map<Integer, MenuIcon> icons = new LinkedHashMap<>();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            icons.put(slot, filler);
        }

        List<Integer> contentSlots = layout.contentSlots();
        contentSlots.forEach(icons::remove);
        int first = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int entry = first + index;
            if (entry >= pageEntries.size()) {
                break;
            }
            icons.put(contentSlots.get(index), pageEntries.get(entry));
        }

        for (Map.Entry<String, MenuButton> button : layout.buttons().entrySet()) {
            String name = button.getKey();
            MenuButton value = button.getValue();
            PageTarget target = target(name, page, pages);
            if (target.hidden()) {
                continue;
            }
            Optional<Runnable> action = target.page()
                    .map(destination -> (Runnable) () -> goToPage.accept(destination));
            String keyBase = switch (name) {
                case PREVIOUS -> "menu_button_previous_page";
                case NEXT -> "menu_button_next_page";
                default -> null;
            };
            String displayName = keyBase == null ? null
                    : pageText(pageLanguage, keyBase + "_name", target.page().orElse(page), pages);
            List<String> lore = keyBase == null ? null
                    : pageLore(pageLanguage, keyBase + "_lore", target.page().orElse(page), pages);
            icons.put(value.slot(), icon(menuId, "button:" + name, value.item(), action, value.sound(),
                    displayName, lore, pages, page, pageLanguage));
        }

        return new MenuView(MessageText.color(title), layout.rows(), icons);
    }

    public synchronized void resetWarnings() {
        warned.clear();
    }

    private record PageTarget(boolean hidden, Optional<Integer> page) {
    }

    private static PageTarget target(String name, int page, int pages) {
        return switch (name) {
            case PREVIOUS -> page > 0 ? new PageTarget(false, Optional.of(page - 1)) : new PageTarget(true, Optional.empty());
            case NEXT -> page < pages - 1 ? new PageTarget(false, Optional.of(page + 1)) : new PageTarget(true, Optional.empty());
            default -> new PageTarget(false, Optional.empty());
        };
    }

    private MenuIcon icon(String menuId, String entry, ItemReference reference, Optional<Runnable> action,
                          Optional<MenuSound> sound, String displayName, List<String> lore, int pages, int page,
                          Function<String, String> pageLanguage) {
        ItemResolveResult resolved = items.resolve(reference);
        if (resolved.isMiss()) {
            warnOnce(menuId, entry, reference);
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta meta = barrier.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(MISSING_ICON_KEY, PersistentDataType.BYTE, (byte) 1);
                meta.setDisplayName(missingName(pageLanguage, reference));
                meta.setLore(missingLore(pageLanguage));
                barrier.setItemMeta(meta);
            }
            return new MenuIcon(barrier, action, sound);
        }
        ItemStack item = resolved.item().orElseThrow().clone();
        if (displayName != null || lore != null) {
            applyText(item, displayName, lore);
        }
        return new MenuIcon(item, action, sound);
    }

    private static void applyText(ItemStack item, String displayName, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (displayName != null) {
            meta.setDisplayName(displayName);
        }
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    private static String pageText(Function<String, String> language, String key, int page, int pages) {
        return MessageText.color(placeholders(language.apply(key), page, pages));
    }

    private static List<String> pageLore(Function<String, String> language, String key, int page, int pages) {
        String text = pageText(language, key, page, pages);
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    private static String missingName(Function<String, String> language, ItemReference reference) {
        return MessageText.color(language.apply(MISSING_NAME_KEY).replace("{item}", nameOf(reference)));
    }

    private static List<String> missingLore(Function<String, String> language) {
        String text = MessageText.color(language.apply(MISSING_LORE_KEY));
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    private static String placeholders(String text, int page, int pages) {
        return text.replace("{page}", Integer.toString(page + 1)).replace("{pages}", Integer.toString(pages));
    }

    private void warnOnce(String menuId, String entry, ItemReference reference) {
        String key = menuId + "\u0000" + entry;
        synchronized (this) {
            if (!warned.add(key)) {
                return;
            }
        }
        warnings.warn(MenuWarning.layoutItemMissing(menuId, entry, reference));
    }

    private static String nameOf(ItemReference reference) {
        String source = reference.source();
        String id = reference.id();
        return source == null || source.isBlank() ? id : source + ":" + id;
    }

    private static NamespacedKey key(String text) {
        return Objects.requireNonNull(NamespacedKey.fromString(text), text);
    }
}
