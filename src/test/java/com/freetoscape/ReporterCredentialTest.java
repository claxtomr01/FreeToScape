package com.freetoscape;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReporterCredentialTest {
    private static final String FIRST = "https://clan.example";
    private static final String SECOND = "https://other.example";
    private static final String TOKEN = "test-only-credential-01234567890123456789";

    @Test public void credentialIsCopiedAndBoundToTheEnteredServer() {
        ReporterCredential credential = new ReporterCredential();
        char[] input = TOKEN.toCharArray();
        credential.use(FIRST + "/", input);
        Arrays.fill(input, '\0');
        assertEquals(TOKEN, credential.forServer(FIRST));
        assertNull(credential.forServer(SECOND));
    }

    @Test public void clearErasesTheStoredArrayAndSuppressesEnvironmentFallback() throws Exception {
        ReporterCredential credential = new ReporterCredential();
        credential.use(FIRST, TOKEN.toCharArray());
        Field field = ReporterCredential.class.getDeclaredField("value");
        field.setAccessible(true);
        char[] stored = (char[]) field.get(credential);
        credential.clear();
        for (char value : stored) assertEquals('\0', value);
        credential.importEnvironment(FIRST, TOKEN);
        assertNull(credential.forServer(FIRST));
    }

    @Test public void environmentFallbackIsBoundOnceAndCannotFollowAChangedReceiver() {
        ReporterCredential credential = new ReporterCredential();
        credential.importEnvironment(FIRST, TOKEN);
        credential.importEnvironment(SECOND, TOKEN);
        assertEquals(TOKEN, credential.forServer(FIRST));
        assertNull(credential.forServer(SECOND));
    }

    @Test public void manualSetupReplacesAndErasesThePreviousCredential() throws Exception {
        ReporterCredential credential = new ReporterCredential();
        credential.importEnvironment(FIRST, TOKEN);
        Field field = ReporterCredential.class.getDeclaredField("value");
        field.setAccessible(true);
        char[] old = (char[]) field.get(credential);
        credential.use(SECOND, (TOKEN + "new").toCharArray());
        for (char value : old) assertEquals('\0', value);
        assertNull(credential.forServer(FIRST));
        assertEquals(TOKEN + "new", credential.forServer(SECOND));
    }

    @Test public void rejectedSetupDoesNotReplaceAnExistingValidCredential() {
        ReporterCredential credential = new ReporterCredential();
        credential.use(FIRST, TOKEN.toCharArray());
        for (String address : new String[]{"http://remote.example", "https://clan.example?token=private", "https://user:pass@clan.example", "http://127.0.0.1:8787/v1/feed"}) {
            try {
                credential.use(address, TOKEN.toCharArray());
                fail("Unsafe receiver accepted");
            } catch (IllegalArgumentException expected) {
                assertEquals(TOKEN, credential.forServer(FIRST));
            }
        }
    }

    @Test public void invalidHeaderCredentialsAreRejectedWithoutRetention() {
        ReporterCredential credential = new ReporterCredential();
        for (char[] value : new char[][]{null, "short".toCharArray(), (TOKEN + "\r\n").toCharArray(), (TOKEN + " ").toCharArray(), new char[257]}) {
            try {
                credential.use(FIRST, value);
                fail("Invalid credential accepted");
            } catch (IllegalArgumentException expected) {
                assertNull(credential.forServer(FIRST));
            }
        }
    }
}
