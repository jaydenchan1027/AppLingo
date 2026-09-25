package dev.applingo;

import android.app.Application;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import java.util.concurrent.*;
import java.util.*;
import rikka.shizuku.Shizuku;

public final class AccessModel extends AndroidViewModel {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final MutableLiveData<AccessGate.Snapshot> state=new MutableLiveData<>();
    private final AccessGate gate;
    private final SharedPreferences prefs;
    private final Shizuku.UserServiceArgs args;
    private ServiceConnection connection;
    private ILocaleService service;
    private boolean cleared, started, operationBusy;
    final MutableLiveData<Boolean> working=new MutableLiveData<>(false);
    final MutableLiveData<String> progress=new MutableLiveData<>("");
    final MutableLiveData<Map<String,String>> languages=new MutableLiveData<>(new HashMap<>());
    final MutableLiveData<ChangeEngine.Result> completed=new MutableLiveData<>();
    final MutableLiveData<Boolean> undoable=new MutableLiveData<>(false);
    private UndoStore undoStore;
    boolean canUndo(){return Boolean.TRUE.equals(undoable.getValue());}
    private void refreshUndoable(){worker.execute(()->undoable.postValue(!undoStore.load().isEmpty()));}
    void acknowledge(){completed.setValue(null);}
    private ChangeEngine.Backend backend(){
        final boolean root=gate.snapshot().mode==AccessGate.Mode.ROOT;final ILocaleService remote=service;
        return new ChangeEngine.Backend(){
            private void check(){if(!root&&(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED||remote==null))throw new SecurityException("Shizuku access was lost.");}
            public String read(String pkg)throws Exception{check();return root?CommandRunner.get(pkg,userId,true):remote.getLocales(pkg,userId);}
            public String write(String pkg,String tags)throws Exception{check();return root?CommandRunner.set(pkg,userId,tags,true):remote.setLocales(pkg,userId,tags);}
        };
    }
    void change(Map<String,String> requested,boolean undo){
        if(operationBusy||gate.snapshot().phase!=AccessGate.Phase.READY)return;
        operationBusy=true;working.setValue(true);progress.setValue(undo?"Restoring languages…":"Saving languages…");
        ChangeEngine.Backend backend=backend();Map<String,String> copy=new LinkedHashMap<>(requested);
        worker.execute(()->{
            ChangeEngine.Progress report=(done,total)->main.post(()->{if(!cleared)progress.setValue((undo?"Restoring ":"Saving ")+done+" / "+total);});
            ChangeEngine.Result result=undo?ChangeEngine.undo(backend,undoStore,report):ChangeEngine.apply(copy,backend,undoStore,report);
            final boolean hasUndo=!undoStore.load().isEmpty();
            main.post(()->{operationBusy=false;if(cleared)return;
                Map<String,String> values=new HashMap<>(languages.getValue());values.putAll(result.values);languages.setValue(values);
                working.setValue(false);progress.setValue("");undoable.setValue(hasUndo);
                if(result.interrupted)disconnect("The operation stopped. Reconnect to check access; completed changes can still be undone.");
                completed.setValue(result);
            });
        });
    }
    void refreshLanguages(List<String> packages){
        if(operationBusy||gate.snapshot().phase!=AccessGate.Phase.READY)return;
        operationBusy=true;working.setValue(true);ChangeEngine.Backend backend=backend();List<String> copy=new ArrayList<>(packages);
            worker.execute(()->{
            Map<String,String> values=new HashMap<>();String failure=null;int done=0;
            for(String pkg:copy){
                try{values.put(pkg,backend.read(pkg));final int n=++done;main.post(()->{if(!cleared)progress.setValue("Checking languages "+n+" / "+copy.size());});}
                catch(Exception e){failure=e.getMessage();}
            }
            final String problem=failure;
            main.post(()->{operationBusy=false;if(cleared)return;Map<String,String> merged=new HashMap<>(languages.getValue());merged.putAll(values);languages.setValue(merged);working.setValue(false);progress.setValue("");if(problem!=null&&values.isEmpty())disconnect("Couldn’t check languages. Reconnect and try again.");});
        }); }
    /** Discover Android users/profiles (main, work, clones) via `cmd user list`. */
    void listUsers(Callback callback){
        if(operationBusy){callback.failed("Wait for the current operation to finish.");return;}
        final boolean root=gate.snapshot().mode==AccessGate.Mode.ROOT;final ILocaleService remote=service;
        worker.execute(()->{
            try{
                String out=root?CommandRunner.listUsers(true):(remote!=null?remote.listUsers():null);
                if(out==null)throw new SecurityException("Shizuku access was lost.");
                final String result=out;
                main.post(()->{if(cleared)return;callback.done(result);});
            }catch(Exception e){final String msg=e.getMessage()==null?"Couldn’t list Android users.":e.getMessage();
                main.post(()->{if(cleared)return;callback.failed(msg);});}
        });
    }
    private long permissionAttempt=-1;
    int userId;
    void setUserId(int id){userId=id;prefs.edit().putInt("user_id",id).apply();}

    private final Shizuku.OnRequestPermissionResultListener permission;
    private final Shizuku.OnBinderDeadListener dead;
    private final Shizuku.OnBinderReceivedListener received;

    public AccessModel(@NonNull Application app) {
        super(app);
        prefs=app.getSharedPreferences("access",0);
        userId=prefs.getInt("user_id",android.os.Process.myUid()/100000);
        undoStore=new UndoStore(app.getSharedPreferences("changes",0));
        refreshUndoable();
        gate=new AccessGate("root".equals(prefs.getString("mode","shizuku"))?AccessGate.Mode.ROOT:AccessGate.Mode.SHIZUKU);
        args=new Shizuku.UserServiceArgs(new ComponentName(app,LocaleService.class)).daemon(false).processNameSuffix("locale_service").debuggable(false).version(2);
    permission=(code,grant)->main.post(()->{
        if(code!=41 || !gate.current(permissionAttempt)) return;
        long ticket=permissionAttempt;permissionAttempt=-1;
        if(grant==PackageManager.PERMISSION_GRANTED) bind(ticket);
        else fail(ticket,"Permission wasn’t granted. Allow AppLingo in Shizuku, then try again.");
    });
    dead=()->main.post(()->{
        if(gate.snapshot().mode==AccessGate.Mode.SHIZUKU) disconnect("Shizuku has stopped. Start it again to continue.");
    });
    received=()->main.post(()->{
        if(started && !cleared && gate.snapshot().mode==AccessGate.Mode.SHIZUKU && gate.snapshot().phase==AccessGate.Phase.SETUP) autoConnect();
    });

        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addBinderReceivedListenerSticky(received);
        publish();
    }
    LiveData<AccessGate.Snapshot> state(){return state;}
    AccessGate.Snapshot snapshot(){return gate.snapshot();}
    void start(){if(started)return;started=true;autoConnect();}
    void select(AccessGate.Mode mode){
        if(operationBusy || gate.snapshot().phase==AccessGate.Phase.CHECKING)return;
        unbind();gate.select(mode);prefs.edit().putString("mode",mode==AccessGate.Mode.ROOT?"root":"shizuku").apply();publish();
    }
    void setup(){if(operationBusy)return;unbind();gate.lost("Choose how AppLingo connects.");publish();}
    void autoConnect(){
        if(cleared || gate.snapshot().phase!=AccessGate.Phase.SETUP)return;
        if(gate.snapshot().mode==AccessGate.Mode.ROOT){
            if(prefs.getBoolean("root_worked",false))connect(false);
        }else try {
            if(Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)connect(false);
        }catch(RuntimeException ignored){ /* Setup remains available. */ }
    }
    void resume(){
        if(!started || cleared || operationBusy || gate.snapshot().phase==AccessGate.Phase.CHECKING)return;
        if(gate.snapshot().phase==AccessGate.Phase.READY)connect(false);
        else autoConnect();
    }
    void connect(boolean requestPermission){
        if(cleared || operationBusy || gate.snapshot().phase==AccessGate.Phase.CHECKING)return;
        AccessGate.Mode mode=gate.snapshot().mode;
        long ticket=gate.begin(mode==AccessGate.Mode.ROOT?"Checking root access…":"Checking Shizuku access…");publish();
        if(mode==AccessGate.Mode.ROOT){
            worker.execute(()->{
                try{
                    String uid=CommandRunner.run(new String[]{"/system/bin/id","-u"},true);
                    if(!"0".equals(uid))throw new IllegalStateException("Root access was denied. Allow AppLingo in your root manager.");
                    CommandRunner.get(getApplication().getPackageName(),userId,true);
                    main.post(()->ready(ticket));
                }catch(Exception e){main.post(()->{prefs.edit().putBoolean("root_worked",false).apply();fail(ticket,"Root is unavailable or permission was denied. Check your root manager, or use Shizuku.");});}
            });
        }else{
            try{
                if(!Shizuku.pingBinder()){fail(ticket,"Shizuku isn’t running. Open Shizuku and start its service first.");return;}
                if(Shizuku.isPreV11()){fail(ticket,"Update Shizuku to a recent version, then try again.");return;}
                if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){bind(ticket);return;}
                if(!requestPermission){fail(ticket,"Shizuku permission is needed. Tap Allow Shizuku access.");return;}
                if(Shizuku.shouldShowRequestPermissionRationale()){fail(ticket,"Open Shizuku → Authorized applications and allow AppLingo, then reconnect.");return;}
                permissionAttempt=ticket;Shizuku.requestPermission(41);
                main.postDelayed(()->fail(ticket,"Permission request timed out. Try again when you’re ready."),60000);
            }catch(Exception e){fail(ticket,"Couldn’t connect to Shizuku. Start it and try again.");}
        }
    }
    private void bind(long ticket){
        if(!gate.current(ticket))return;
        unbind();
        connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){
                if(!gate.current(ticket)||cleared)return;
                ILocaleService remote=ILocaleService.Stub.asInterface(binder);service=remote;
                worker.execute(()->{
                    try{remote.getLocales(getApplication().getPackageName(),userId);main.post(()->ready(ticket));}
                    catch(Exception e){main.post(()->fail(ticket,"Shizuku connected, but Android refused locale access. Check its authorization and try again."));}
                });
            }
            public void onBindingDied(ComponentName name){if(connection==this&&!cleared)disconnect("Shizuku connection expired. Reconnect to continue.");}
            public void onNullBinding(ComponentName name){if(connection==this&&!cleared)disconnect("Shizuku could not start the service. Restart Shizuku and reconnect.");}
            public void onServiceDisconnected(ComponentName name){if(connection==this && !cleared)disconnect("Shizuku disconnected. Reconnect to continue.");}
        };
        try{Shizuku.bindUserService(args,connection);}
        catch(Exception e){fail(ticket,"Couldn’t start the Shizuku service. Restart Shizuku and try again.");}
        main.postDelayed(()->{if(gate.current(ticket)){unbind();fail(ticket,"Connection timed out. Restart Shizuku and try again.");}},15000);
    }
    private void ready(long ticket){if(cleared||!gate.ready(ticket))return;if(gate.snapshot().mode==AccessGate.Mode.ROOT)prefs.edit().putBoolean("root_worked",true).apply();publish();}
    private void fail(long ticket,String message){if(!cleared&&gate.fail(ticket,message))publish();}
    private void publish(){if(!cleared)state.setValue(gate.snapshot());}
    private void disconnect(String message){service=null;permissionAttempt=-1;gate.lost(message);publish();}
    private void unbind(){
        ServiceConnection old=connection;connection=null;service=null;
        if(old!=null)try{Shizuku.unbindUserService(args,old,true);}catch(Exception ignored){}
    }
    interface Callback{void done(String value);void failed(String message);}
    void locale(String pkg,String tags,boolean write,Callback callback){
        if(operationBusy){callback.failed("Wait for the current operation to finish.");return;}
        if(gate.snapshot().phase!=AccessGate.Phase.READY){callback.failed("Connect before changing a language.");return;}
        operationBusy=true;
        final boolean root=gate.snapshot().mode==AccessGate.Mode.ROOT;
        final ILocaleService remote=service;
        worker.execute(()->{
            try{
                String value;
                if(root)value=write?CommandRunner.set(pkg,userId,tags,true):CommandRunner.get(pkg,userId,true);
                else{
                    if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED||remote==null)throw new SecurityException("Shizuku access was lost.");
                    value=write?remote.setLocales(pkg,userId,tags):remote.getLocales(pkg,userId);
                }
                main.post(()->{operationBusy=false;if(cleared)return;if(gate.snapshot().phase==AccessGate.Phase.READY)callback.done(value);else callback.failed("Access was lost. Reconnect and check the saved language.");});
            }catch(Exception e){main.post(()->{
                operationBusy=false;if(cleared)return;
                // Re-enter setup on failure: the next Continue always verifies real access again.
                if(root)prefs.edit().putBoolean("root_worked",false).apply();
                disconnect("Access needs checking. Reconnect to continue.");
                callback.failed(e.getMessage()==null?"Android refused the request.":e.getMessage());
            });}
        });
    }
    @Override protected void onCleared(){
        cleared=true;main.removeCallbacksAndMessages(null);unbind();
        Shizuku.removeRequestPermissionResultListener(permission);Shizuku.removeBinderDeadListener(dead);Shizuku.removeBinderReceivedListener(received);
        worker.shutdownNow();
    }
}
