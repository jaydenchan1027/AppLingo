package dev.applingo;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ChangeEngineTest {
 static final class Memory implements ChangeEngine.Store {
  Map<String,ChangeEngine.Entry> entries=new LinkedHashMap<>();boolean fail;
  public Map<String,ChangeEngine.Entry> load(){return new LinkedHashMap<>(entries);}
  public void save(Map<String,ChangeEngine.Entry> next)throws Exception{if(fail)throw new Exception("Storage unavailable");entries=new LinkedHashMap<>(next);}
 }
 static final class Backend implements ChangeEngine.Backend {
  Map<String,String> values=new LinkedHashMap<>();String failPackage;int writes;Memory journal;
  Backend(Memory store){journal=store;values.put("dev.first","en");values.put("dev.second","");}
  public String read(String pkg){return values.get(pkg);}
  public String write(String pkg,String tags)throws Exception{assertTrue(journal.entries.containsKey(pkg));if(pkg.equals(failPackage))throw new Exception("Access lost");writes++;values.put(pkg,tags);return tags;}
 }
 private Map<String,String> requests(){Map<String,String> r=new LinkedHashMap<>();r.put("dev.first","fr");r.put("dev.second","fr");return r;}
 @Test public void bulkAndUndoRestoreDistinctPreviousLanguages(){Memory s=new Memory();Backend b=new Backend(s);ChangeEngine.Result r=ChangeEngine.apply(requests(),b,s,(d,t)->{});assertEquals(2,r.changed);assertFalse(r.interrupted);r=ChangeEngine.undo(b,s,(d,t)->{});assertEquals(2,r.changed);assertEquals("en",b.values.get("dev.first"));assertEquals("",b.values.get("dev.second"));assertTrue(s.entries.isEmpty());}
 @Test public void partialFailureKeepsUndoForCompletedAndUncertainWrite(){Memory s=new Memory();Backend b=new Backend(s);b.failPackage="dev.second";ChangeEngine.Result r=ChangeEngine.apply(requests(),b,s,(d,t)->{});assertTrue(r.interrupted);assertEquals(1,r.changed);assertEquals(2,s.entries.size());b.failPackage=null;r=ChangeEngine.undo(b,s,(d,t)->{});assertEquals(1,r.changed);assertEquals(1,r.unchanged);assertTrue(s.entries.isEmpty());}
 @Test public void undoDoesNotOverwriteAnExternalChange(){Memory s=new Memory();Backend b=new Backend(s);ChangeEngine.apply(requests(),b,s,(d,t)->{});b.values.put("dev.first","de");ChangeEngine.Result r=ChangeEngine.undo(b,s,(d,t)->{});assertEquals("de",b.values.get("dev.first"));assertEquals(1,r.issues.size());}
 @Test public void undoClearsJournalForAppsChangedElsewhere(){Memory s=new Memory();Backend b=new Backend(s);ChangeEngine.apply(requests(),b,s,(d,t)->{});b.values.put("dev.first","de");ChangeEngine.undo(b,s,(d,t)->{});
  // Both the restored app and the externally-changed app must be removed from the journal,
  // otherwise stale entries keep Undo available forever.
  assertFalse(s.entries.containsKey("dev.first"));assertFalse(s.entries.containsKey("dev.second"));assertTrue(s.entries.isEmpty());}
 @Test public void failedJournalSavePreventsPrivilegedWrites(){Memory s=new Memory();s.fail=true;Backend b=new Backend(s);ChangeEngine.Result r=ChangeEngine.apply(requests(),b,s,(d,t)->{});assertTrue(r.interrupted);assertEquals(0,b.writes);}
 @Test public void matchingLanguageIsSkippedWithoutUndoEntry(){Memory s=new Memory();Backend b=new Backend(s);ChangeEngine.Result r=ChangeEngine.apply(Collections.singletonMap("dev.first","en"),b,s,(d,t)->{});assertEquals(1,r.unchanged);assertEquals(0,b.writes);assertTrue(s.entries.isEmpty());}
 @Test public void undoRetryAfterInterruptedRestore(){Memory s=new Memory();Backend b=new Backend(s);ChangeEngine.apply(requests(),b,s,(d,t)->{});b.failPackage="dev.second";ChangeEngine.Result r=ChangeEngine.undo(b,s,(d,t)->{});assertTrue(r.interrupted);assertFalse(s.entries.containsKey("dev.first"));assertTrue(s.entries.containsKey("dev.second"));b.failPackage=null;ChangeEngine.undo(b,s,(d,t)->{});assertTrue(s.entries.isEmpty());}
 @Test public void mismatchedReadbackStopsBatch(){Memory s=new Memory();ChangeEngine.Backend b=new ChangeEngine.Backend(){public String read(String pkg){return "en";}public String write(String pkg,String tags){return "de";}};ChangeEngine.Result r=ChangeEngine.apply(requests(),b,s,(d,t)->{});assertTrue(r.interrupted);assertEquals(1,s.entries.size());assertEquals(0,r.changed);}
}
