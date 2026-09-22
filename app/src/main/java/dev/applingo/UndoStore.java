package dev.applingo;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
final class UndoStore implements ChangeEngine.Store {
    private final SharedPreferences prefs;
    UndoStore(SharedPreferences prefs){this.prefs=prefs;}
    public Map<String,ChangeEngine.Entry> load(){
        Map<String,ChangeEngine.Entry> result=new LinkedHashMap<>();
        try{JSONObject json=new JSONObject(prefs.getString("undo","{}"));Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();JSONObject entry=json.getJSONObject(key);result.put(key,new ChangeEngine.Entry(entry.getString("before"),entry.getString("after")));}}
        catch(JSONException e){throw new IllegalStateException("Couldn’t read saved undo history.",e);}return result;
    }
    public void save(Map<String,ChangeEngine.Entry> entries) throws Exception {
        JSONObject json=new JSONObject();for(Map.Entry<String,ChangeEngine.Entry> e:entries.entrySet()){
            JSONObject value=new JSONObject();value.put("before",e.getValue().before);value.put("after",e.getValue().after);json.put(e.getKey(),value);
        }
        if(!prefs.edit().putString("undo",json.toString()).commit())throw new IllegalStateException("Couldn’t save undo history. No further changes were made.");
    }
}
