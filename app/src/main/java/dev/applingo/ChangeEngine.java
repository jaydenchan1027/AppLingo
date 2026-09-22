package dev.applingo;

import java.util.*;

/** Serial, verified changes with durable write-ahead undo and conflict protection. */
final class ChangeEngine {
    interface Backend { String read(String pkg) throws Exception; String write(String pkg,String tags) throws Exception; }
    interface Store { Map<String,Entry> load(); void save(Map<String,Entry> entries) throws Exception; }
    interface Progress { void update(int completed,int total); }
    static final class Entry {
        final String before,after;
        Entry(String before,String after){this.before=before;this.after=after;}
    }
    static final class Result {
        final Map<String,String> values=new LinkedHashMap<>();
        final List<String> issues=new ArrayList<>();
        int changed,unchanged; boolean interrupted;
    }
    static Result apply(Map<String,String> requested,Backend backend,Store store,Progress progress) {
        Result result=new Result();Map<String,Entry> journal=new LinkedHashMap<>();int completed=0;
        try {
            store.save(journal);
            for(Map.Entry<String,String> request:requested.entrySet()){
                String pkg=request.getKey(),target=LocaleCommands.normalize(request.getValue());
                String before=backend.read(pkg);
                if(before.equals(target)){result.unchanged++;result.values.put(pkg,before);}
                else {
                    journal.put(pkg,new Entry(before,target));store.save(journal);
                    String actual=backend.write(pkg,target);
                    if(!actual.equals(target))throw new IllegalStateException("Android returned a different language for "+pkg);
                    result.values.put(pkg,actual);result.changed++;
                }
                progress.update(++completed,requested.size());
            }
        } catch(Exception e){result.interrupted=true;result.issues.add(message(e));}
        return result;
    }
    static Result undo(Backend backend,Store store,Progress progress){
        Result result=new Result();Map<String,Entry> journal=new LinkedHashMap<>(store.load());int total=journal.size(),completed=0;
        try {
            for(String pkg:new ArrayList<>(journal.keySet())){
                Entry entry=journal.get(pkg);String current=backend.read(pkg);
                if(current.equals(entry.before)){result.unchanged++;result.values.put(pkg,current);}
                else if(!current.equals(entry.after)){
                    result.issues.add(pkg+": changed elsewhere; left unchanged.");progress.update(++completed,total);continue;
                } else {
                    String actual=backend.write(pkg,entry.before);
                    if(!actual.equals(entry.before))throw new IllegalStateException("Couldn’t verify restore for "+pkg);
                    result.values.put(pkg,actual);result.changed++;
                }
                journal.remove(pkg);store.save(journal);progress.update(++completed,total);
            }
        }catch(Exception e){result.interrupted=true;result.issues.add(message(e));}
        return result;
    }
    private static String message(Exception e){return e.getMessage()==null?"Android refused the request.":e.getMessage();}
}
