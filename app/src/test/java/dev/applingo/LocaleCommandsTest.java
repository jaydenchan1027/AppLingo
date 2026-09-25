package dev.applingo;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocaleCommandsTest {
    @Test public void resetOmitsLocales() { assertArrayEquals(new String[]{"/system/bin/cmd","locale","set-app-locales","com.example.app","--user","10"},LocaleCommands.command(true,"com.example.app",10,"")); }
    @Test public void traditionalChinesePreservesScriptAndRegion() { assertEquals("zh-Hant-HK",LocaleCommands.normalize("zh-hant-hk")); }
    @Test public void normalizesAndDeduplicatesFallbacks() { assertEquals("en-US,ja",LocaleCommands.normalize("en-us,ja,en-US")); }
    @Test public void readbackParsesLocales() { assertEquals("zh-Hant-HK",LocaleCommands.parse("Locales for com.example.app for user 10 are [zh-Hant-HK]\n")); }
    @Test public void readbackParsesReset() { assertEquals("",LocaleCommands.parse("Locales for com.example.app for user 0 are []")); }
    @Test public void parseIsIndependentOfSurroundingText() { assertEquals("en,ja",LocaleCommands.parse("WARNING: something\nLocales for com.example.app are [en,ja]\nDone.")); }
    @Test public void parseAcceptsNonEnglishWording() { assertEquals("zh-Hant-HK",LocaleCommands.parse("应用语言设置为 [zh-Hant-HK]")); }
    @Test(expected=IllegalStateException.class) public void rejectionCannotLookLikeSuccess() { LocaleCommands.parse("SecurityException: denied"); }
    @Test(expected=IllegalStateException.class) public void parseRejectsOutputWithoutBrackets() { LocaleCommands.parse("no locale information here"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsPackageInjection() { LocaleCommands.command(true,"com.test;id",0,"en"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLocaleInjection() { LocaleCommands.command(true,"com.test",0,"en;id"); }
    @Test public void skipsEmptySegmentsInsteadOfRejecting() { assertEquals("en,ja",LocaleCommands.normalize("en,,ja")); }
    @Test public void toleratesTrailingAndLeadingCommas() { assertEquals("en",LocaleCommands.normalize(",en,")); }
    @Test public void toleratesWhitespaceAroundTags() { assertEquals("en-US,ja",LocaleCommands.normalize(" en-US , ja ")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsCrossUserSentinel() { LocaleCommands.command(true,"com.test",-1,"en"); }
}
