package dev.applingo;
import org.junit.Test;
import static org.junit.Assert.*;
public class AccessGateTest {
 @Test public void firstLaunchNeedsAccess(){assertEquals(AccessGate.Phase.SETUP,new AccessGate(AccessGate.Mode.SHIZUKU).snapshot().phase);}
 @Test public void checkingDoesNotUnlockApps(){AccessGate g=new AccessGate(AccessGate.Mode.ROOT);g.begin("Checking");assertEquals(AccessGate.Phase.CHECKING,g.snapshot().phase);}
 @Test public void onlyCompletedCheckUnlocksApps(){AccessGate g=new AccessGate(AccessGate.Mode.ROOT);assertTrue(g.ready(g.begin("Checking")));assertEquals(AccessGate.Phase.READY,g.snapshot().phase);}
 @Test public void denialReturnsToSetup(){AccessGate g=new AccessGate(AccessGate.Mode.SHIZUKU);g.fail(g.begin("Checking"),"Denied");assertEquals(AccessGate.Phase.SETUP,g.snapshot().phase);assertEquals("Denied",g.snapshot().message);}
 @Test public void revokedAccessClosesHome(){AccessGate g=new AccessGate(AccessGate.Mode.ROOT);g.ready(g.begin("Checking"));g.lost("Revoked");assertEquals(AccessGate.Phase.SETUP,g.snapshot().phase);}
 @Test public void oldBackendCannotGrantNewBackend(){AccessGate g=new AccessGate(AccessGate.Mode.SHIZUKU);long token=g.begin("Checking");g.select(AccessGate.Mode.ROOT);assertFalse(g.ready(token));assertEquals(AccessGate.Phase.SETUP,g.snapshot().phase);}
 @Test public void staleFailureCannotUndoNewSuccess(){AccessGate g=new AccessGate(AccessGate.Mode.ROOT);long old=g.begin("First");long next=g.begin("Second");g.ready(next);assertFalse(g.fail(old,"Failure"));assertEquals(AccessGate.Phase.READY,g.snapshot().phase);}
 @Test public void delayedCallbackCannotUndoDisconnection(){AccessGate g=new AccessGate(AccessGate.Mode.SHIZUKU);long token=g.begin("Checking");g.lost("Disconnected");assertFalse(g.ready(token));}
 @Test public void completedCheckCannotTimeOut(){AccessGate g=new AccessGate(AccessGate.Mode.SHIZUKU);long token=g.begin("Checking");g.ready(token);assertFalse(g.fail(token,"Timeout"));}
}
