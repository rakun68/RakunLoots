package me.rakkoon69.rakunloots.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.rakkoon69.rakunloots.command.SampleCommands.BASE;
import static me.rakkoon69.rakunloots.command.SampleCommands.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispatch and completion of the sample command tree through a fake sender: nesting, aliases, the two-line failure
 * with its underline, and what a sender may not run.
 */
class CommandDispatchTest {

    private final SampleCommands sample = new SampleCommands();
    private final FakeCommandSender admin = sample.admin();

    @Test
    void nodesNestAndRunWithTheirParsedArguments() {
        assertTrue(sample.registry.dispatch(admin, "rkl remove world creative"));
        assertTrue(sample.registry.dispatch(admin, "rkl give vote_crate Alex 3"));

        assertEquals(List.of("remove world creative", "give vote_crate Alex 3"), sample.ran);
        assertEquals(List.of(), admin.messages());
    }

    @Test
    void aRootAndANodeAnswerToTheirAliases() {
        sample.registry.dispatch(admin, "rkloots t world now");
        sample.registry.dispatch(admin, "RKL T creative NOW");

        assertEquals(List.of("timer world false", "timer creative false"), sample.ran);
    }

    @Test
    void aNodePermissionIsTheRootBaseAndItsPathBelowTheRoot() {
        CommandNode root = sample.root();
        CommandNode remove = root.children().get(1);
        CommandNode world = remove.children().get(0);

        assertEquals(List.of("remove", "world"), world.path());
        assertEquals("rakunloots.command.remove.world", world.permission().orElseThrow());
        assertEquals("rakunloots.command.give", root.children().get(0).permission().orElseThrow());
        assertEquals(java.util.Optional.empty(), root.permission());
    }

    @Test
    void aFailedCommandShowsTheTypedLineWithTheFailingArgumentUnderlinedThenTheReasonAndType() {
        List<String> shown = sample.dispatch(admin, "rkl give nosuch Steve 2");

        assertEquals(List.of(
                PREFIX + " &f/rkl give &c&nnosuch&r&f Steve 2",
                "&8> &cno loot has the id &fnosuch&c&7, expected &ea loot id&7."
        ), shown);
        assertEquals(List.of(), sample.ran);
    }

    @Test
    void theFirstFailingTokenStopsTheParse() {
        List<String> shown = sample.dispatch(admin, "rkl give nosuch Nobody 999");

        assertEquals(PREFIX + " &f/rkl give &c&nnosuch&r&f Nobody 999", shown.get(0));
        assertEquals(2, shown.size());
    }

    @Test
    void aMissingArgumentUnderlinesTheEndOfTheLine() {
        assertEquals(List.of(
                PREFIX + " &f/rkl give &c&n &r&f",
                "&8> &cthe &floot &cargument is missing&7, expected &ea loot id&7."
        ), sample.dispatch(admin, "rkl give"));
        assertEquals(List.of(
                PREFIX + " &f/rkl timer world &c&n &r&f",
                "&8> &cthe &fwhen &cargument is missing&7, expected &eone of now&7."
        ), sample.dispatch(admin, "rkl timer world   "));
    }

    @Test
    void anExtraArgumentIsUnderlined() {
        assertEquals(List.of(
                PREFIX + " &f/rkl timer world now --force &c&nextra&r&f",
                "&8> &ctoo many arguments, &fextra &cis extra&7, expected &eno more arguments&7."
        ), sample.dispatch(admin, "rkl timer world now --force extra"));
    }

    @Test
    void anUnknownSubcommandListsWhatTheSenderMayRunAndOffersCloseMatches() {
        assertEquals(List.of(
                PREFIX + " &f/rkl &c&nremov&r&f",
                "&8> &cno subcommand named &fremov&c&7, expected &eone of give, remove, timer, whois, help&7."
                        + " Did you mean &eremove&7?"
        ), sample.dispatch(admin, "rkl remov"));
    }

    @Test
    void aNodeWithOnlyChildrenAsksForASubcommandAtTheEndOfTheLine() {
        assertEquals(List.of(
                PREFIX + " &f/rkl remove &c&n &r&f",
                "&8> &ca subcommand is missing&7, expected &eone of world, near&7."
        ), sample.dispatch(admin, "rkl remove"));
    }

    @Test
    void aBareRootPointsToHelp() {
        assertEquals(List.of(PREFIX + " Use &e/rkloots help &7to list the commands you can run."),
                sample.dispatch(admin, "rkloots"));
    }

    @Test
    void completionOffersSubcommandsThenArgumentsAndStopsAfterTheLastArgument() {
        assertEquals(List.of("give", "help", "remove", "timer", "whois"), sample.registry.complete(admin, "rkl "));
        assertEquals(List.of("remove"), sample.registry.complete(admin, "rkl rem"));
        assertEquals(List.of("near", "world"), sample.registry.complete(admin, "rkl remove "));
        assertEquals(List.of(), sample.registry.complete(admin, "rkl give nosuch "));
        assertEquals(List.of(), sample.registry.complete(admin, "rkl timer world now --force "));
    }

    @Test
    void aSenderWithoutAPermissionNeitherSeesNorRunsThatNodeAndItAnswersAsUnknown() {
        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".give", BASE + ".help");

        List<String> hidden = sample.dispatch(limited, "rkl timer world now");
        List<String> unknown = sample.dispatch(limited, "rkl nonesuch world now");

        assertEquals(List.of(
                PREFIX + " &f/rkl &c&ntimer&r&f world now",
                "&8> &cno subcommand named &ftimer&c&7, expected &eone of give, help&7."
        ), hidden);
        assertEquals(unknown.get(1).replace("nonesuch", "timer"), hidden.get(1));
        assertEquals(List.of(), sample.ran);
        assertEquals(List.of("give", "help"), sample.registry.complete(limited, "rkl "));
        assertEquals(List.of(), sample.registry.complete(limited, "rkl timer "));
        assertEquals(List.of(), sample.registry.complete(limited, "rkl t"));
    }

    @Test
    void aNodeBelowAParentTheSenderMayNotRunIsOutOfReachEvenWithItsOwnPermission() {
        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".remove.world");

        sample.dispatch(limited, "rkl remove world creative");

        assertEquals(List.of(), sample.ran);
        assertEquals(List.of(), sample.registry.complete(limited, "rkl remove "));
    }

    @Test
    void aSenderMayRunOneChildAndNotItsSibling() {
        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".remove", BASE + ".remove.world");

        sample.dispatch(limited, "rkl remove world creative");
        sample.dispatch(limited, "rkl remove near 5");

        assertEquals(List.of("remove world creative"), sample.ran);
        assertEquals(List.of("world"), sample.registry.complete(limited, "rkl remove "));
    }

    @Test
    void aRootTheSenderMayNotRunAnswersUnknown() {
        CommandNode locked = CommandNode.builder("secret")
                .permission("rakunloots.secret")
                .handler(inv -> sample.ran.add("secret"))
                .build();
        sample.registry.register(locked);
        FakeCommandSender nobody = new FakeCommandSender("Nobody");

        assertFalse(sample.registry.dispatch(nobody, "secret"));
        assertEquals(List.of(PREFIX + " &cUnknown command."), nobody.messages());
        assertEquals(List.of(), sample.registry.complete(nobody, "secret "));
        assertEquals(List.of(), sample.ran);
    }

    @Test
    void aRootWhoseChildrenTheSenderMayNotRunAnswersUnknown() {
        FakeCommandSender nobody = new FakeCommandSender("Nobody");

        assertFalse(sample.registry.dispatch(nobody, "rkl give airdrops"));
        assertFalse(sample.registry.dispatch(nobody, "rkl"));

        assertEquals(List.of(PREFIX + " &cUnknown command.", PREFIX + " &cUnknown command."), nobody.messages());
        assertEquals(List.of(), sample.ran);
    }

    @Test
    void aRootNameOrAliasCannotBeRegisteredTwice() {
        assertThrows(IllegalArgumentException.class, () -> sample.registry.register(
                CommandNode.builder("other").aliases("RKLOOTS").handler(inv -> { }).build()));
    }

    @Test
    void aNodeNeedsAHandlerOrChildrenAndOptionalArgumentsComeLast() {
        assertThrows(IllegalStateException.class, () -> CommandNode.builder("empty").build());
        assertThrows(IllegalArgumentException.class, () -> CommandNode.builder("bad")
                .argument(CommandArgument.optional("a", LootIdArgument.INSTANCE))
                .argument(CommandArgument.required("b", LootIdArgument.INSTANCE)));
        assertThrows(IllegalStateException.class, () -> CommandNode.builder("dup")
                .child(CommandNode.builder("x").aliases("y").handler(inv -> { }).build())
                .child(CommandNode.builder("y").handler(inv -> { }).build())
                .build());
    }
}
