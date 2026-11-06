package me.rakkoon69.rakunloots.command.argument;

import me.rakkoon69.rakunloots.command.FakeCommandSender;
import me.rakkoon69.rakunloots.command.SampleCommands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.rakkoon69.rakunloots.command.SampleCommands.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each argument type through a typed line and a fake sender: it parses a good value, refuses a bad one with its reason
 * and the type it expected, completes a partial token, and offers close matches where it should. The messages are the
 * shipped English ones.
 */
class ArgumentTypesTest {

    private final SampleCommands sample = new SampleCommands();
    private final FakeCommandSender admin = sample.admin();

    private List<String> complete(String line) {
        return sample.registry.complete(admin, line);
    }

    /**
     * The two lines of a failure: the typed line with the underline, then the reason and the expected type.
     */
    private static List<String> failure(String underlinedLine, String reasonAndType) {
        return List.of(PREFIX + " &f/" + underlinedLine, "&8> " + reasonAndType);
    }

    @Test
    void aLootIdParsesIgnoringCaseAndGivesTheIdAsTheServerSpellsIt() {
        sample.dispatch(admin, "rkl give AirDrops");

        assertEquals(List.of("give airdrops - 0"), sample.ran);
    }

    @Test
    void aLootIdMissNamesTheReasonAndTheTypeAndOffersNoCloseMatchWhenNoneIsClose() {
        assertEquals(
                failure("rkl give &c&nnosuch&r&f", "&cno loot has the id &fnosuch&c&7, expected &ea loot id&7."),
                sample.dispatch(admin, "rkl give nosuch"));
        assertEquals(List.of(), sample.ran);
    }

    @Test
    void aLootIdMissOffersAtMostThreeCloseMatches() {
        assertEquals(
                failure("rkl give &c&nairdro&r&f",
                        "&cno loot has the id &fairdro&c&7, expected &ea loot id&7."
                                + " Did you mean &eairdrop, airdrop1, airdrop2&7?"),
                sample.dispatch(admin, "rkl give airdro"));
    }

    @Test
    void aLootIdCompletesAPartialTokenInOrderAndNothingElse() {
        assertEquals(List.of("airdrop", "airdrop1", "airdrop2", "airdrop3", "airdrops"), complete("rkl give air"));
        assertEquals(List.of("vote_crate"), complete("rkl give VOTE"));
        assertEquals(List.of(), complete("rkl give zzz"));
    }

    @Test
    void aWorldParsesAndAMissOffersTheCloseWorld() {
        sample.dispatch(admin, "rkl remove world WORLD_NETHER");
        assertEquals(List.of("remove world world_nether"), sample.ran);

        assertEquals(
                failure("rkl remove world &c&nwrld&r&f",
                        "&cno world is named &fwrld&c&7, expected &ea world name&7. Did you mean &eworld&7?"),
                sample.dispatch(admin, "rkl remove world wrld"));
    }

    @Test
    void aWorldCompletesAPartialToken() {
        assertEquals(List.of("world", "world_nether", "world_the_end"), complete("rkl remove world wor"));
        assertEquals(List.of("creative", "world", "world_nether", "world_the_end"), complete("rkl remove world "));
    }

    @Test
    void aPlayerParsesOnlineOrOfflineAndAMissOffersTheCloseNames() {
        sample.dispatch(admin, "rkl give airdrops steven");
        assertEquals(List.of("give airdrops Steven 0"), sample.ran);

        assertEquals(
                failure("rkl give airdrops &c&nStve&r&f",
                        "&cno player is named &fStve&c&7, expected &ea player name&7. Did you mean &eSteve, Steven&7?"),
                sample.dispatch(admin, "rkl give airdrops Stve"));
    }

    @Test
    void aPlayerCompletesOnlyOnlineNamesButStillTakesAnOfflineOne() {
        assertEquals(List.of("Steve"), complete("rkl give airdrops st"));
        sample.dispatch(admin, "rkl give airdrops Steven");
        assertEquals(List.of("give airdrops Steven 0"), sample.ran);
    }

    @Test
    void anAmountCompletesItsSuggestionsWithinItsBounds() {
        IntegerArgument amount = new IntegerArgument(1, 1000, 1, 64, 5000);
        assertEquals(List.of("1", "64"), amount.complete("", null));
        assertEquals(List.of("64"), amount.complete("6", null));
        assertEquals(List.of(), new IntegerArgument(1, 10).complete("", null));
    }

    @Test
    void anOnlinePlayerRefusesAKnownPlayerWhoIsOfflineAndAnUnknownOneWithTheirOwnReasons() {
        sample.dispatch(admin, "rkl whois Steven alex");
        assertEquals(List.of("whois Steven Alex"), sample.ran);

        assertEquals(
                failure("rkl whois Steve &c&nSteven&r&f",
                        "&c&fSteven &cis not online&7, expected &ethe name of a player who is online&7."),
                sample.dispatch(admin, "rkl whois Steve Steven"));
        assertEquals(
                failure("rkl whois Steve &c&nAlexx&r&f",
                        "&cno player is named &fAlexx&c&7, expected &ethe name of a player who is online&7."
                                + " Did you mean &eAlex&7?"),
                sample.dispatch(admin, "rkl whois Steve Alexx"));
    }

    @Test
    void anOnlinePlayerCompletesOnlyThePlayersWhoAreOnline() {
        assertEquals(List.of("Alex", "Steve"), complete("rkl whois Steve "));
    }

    @Test
    void anIntegerParsesWithinItsBoundsAndRefusesOutsideThemWithTheBoundsInTheType() {
        sample.dispatch(admin, "rkl give airdrops Steve 64");
        assertEquals(List.of("give airdrops Steve 64"), sample.ran);

        String type = "&7, expected &ea whole number from 1 to 64&7.";
        assertEquals(failure("rkl give airdrops Steve &c&n0&r&f", "&c&f0 &cis below 1" + type),
                sample.dispatch(admin, "rkl give airdrops Steve 0"));
        assertEquals(failure("rkl give airdrops Steve &c&n65&r&f", "&c&f65 &cis above 64" + type),
                sample.dispatch(admin, "rkl give airdrops Steve 65"));
        assertEquals(failure("rkl give airdrops Steve &c&n2.5&r&f", "&c&f2.5 &cis not a whole number" + type),
                sample.dispatch(admin, "rkl give airdrops Steve 2.5"));
        assertEquals(List.of(), complete("rkl give airdrops Steve "));
    }

    @Test
    void aRadiusParsesADecimalInBlocksAndRefusesWhatIsOutsideItsRange() {
        sample.dispatch(admin, "rkl remove near 12.5");
        assertEquals(List.of("remove near 12.5"), sample.ran);

        String type = "&7, expected &ea radius in blocks, from 1 to 100&7.";
        assertEquals(failure("rkl remove near &c&n0.5&r&f", "&c&f0.5 &cis outside 1 to 100 blocks" + type),
                sample.dispatch(admin, "rkl remove near 0.5"));
        assertEquals(failure("rkl remove near &c&n101&r&f", "&c&f101 &cis outside 1 to 100 blocks" + type),
                sample.dispatch(admin, "rkl remove near 101"));
        assertEquals(failure("rkl remove near &c&nabc&r&f", "&c&fabc &cis not a number" + type),
                sample.dispatch(admin, "rkl remove near abc"));
        assertEquals(failure("rkl remove near &c&nNaN&r&f", "&c&fNaN &cis outside 1 to 100 blocks" + type),
                sample.dispatch(admin, "rkl remove near NaN"));
        assertEquals(List.of(), complete("rkl remove near "));
    }

    @Test
    void aLiteralChoiceParsesIgnoringCaseRefusesOtherWordsAndCompletes() {
        sample.dispatch(admin, "rkl timer world NOW --FORCE");
        assertEquals(List.of("timer world true"), sample.ran);

        assertEquals(
                failure("rkl timer world &c&nnope&r&f",
                        "&c&fnope &cis not one of the choices&7, expected &eone of now&7. Did you mean &enow&7?"),
                sample.dispatch(admin, "rkl timer world nope"));
        assertEquals(List.of("now"), complete("rkl timer world n"));
        assertEquals(List.of("--force"), complete("rkl timer world now --f"));
    }
}
