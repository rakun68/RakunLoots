package me.rakkoon69.rakunloots.config;

import java.util.Set;

interface MessageResolver {
    String resolve(String lootId, String key);

    Set<String> keys(String languageId);
}
