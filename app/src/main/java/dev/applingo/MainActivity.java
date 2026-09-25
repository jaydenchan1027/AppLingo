package dev.applingo;

import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import androidx.transition.TransitionManager;
import com.google.android.material.bottomsheet.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.*;
import com.google.android.material.transition.MaterialFadeThrough;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private AccessModel access;
    private FrameLayout root;
    private View setup,home;
    private MaterialCardView rootCard,shizukuCard;
    private MaterialButton allow,openShizuku,statusButton;
    private TextView accessMessage,count,empty;
    private TextInputEditText search;
    private Chip allApps,favoritesOnly;
    private MaterialButton selectionButton,actionsButton;
    private final Set<String> favorites=new HashSet<>(),selectedApps=new LinkedHashSet<>();
    private SharedPreferences uiPrefs;
    private boolean selecting;
    private List<String> editorSupported=Collections.emptyList();
    private View accessProgress,actionProgress;
    private RecyclerView appList;
    private final ArrayList<AppEntry> apps=new ArrayList<>(),shown=new ArrayList<>();
    private final Map<String,String> knownLocales=new HashMap<>();
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final AppAdapter adapter=new AppAdapter();
    private boolean loading=true,busy,firstResume=true,pendingRefresh;
    private BottomSheetDialog activeSheet;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        boolean light=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat insets=new WindowInsetsControllerCompat(getWindow(),getWindow().getDecorView());
        insets.setAppearanceLightStatusBars(light);insets.setAppearanceLightNavigationBars(light);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        root=new FrameLayout(this);root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{androidx.core.graphics.Insets b=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
        setup=getLayoutInflater().inflate(R.layout.screen_setup,root,false);home=getLayoutInflater().inflate(R.layout.screen_home,root,false);
        root.addView(home);root.addView(setup);home.setVisibility(View.GONE);
        rootCard=findViewById(R.id.choose_root);shizukuCard=findViewById(R.id.choose_shizuku);
        allow=findViewById(R.id.allow_access);openShizuku=findViewById(R.id.open_shizuku);accessMessage=findViewById(R.id.access_message);accessProgress=findViewById(R.id.access_progress);
        statusButton=findViewById(R.id.access_status);search=findViewById(R.id.search_apps);allApps=findViewById(R.id.show_all);count=findViewById(R.id.app_count);empty=findViewById(R.id.empty_apps);actionProgress=findViewById(R.id.action_progress);appList=findViewById(R.id.app_list);
        uiPrefs=getSharedPreferences("ui",0);favorites.addAll(uiPrefs.getStringSet("favorites",Collections.emptySet()));
        favoritesOnly=findViewById(R.id.favorites_only);selectionButton=findViewById(R.id.select_apps);actionsButton=findViewById(R.id.app_actions);
        if(saved!=null){selecting=saved.getBoolean("selecting");ArrayList<String> ids=saved.getStringArrayList("selected");if(ids!=null)selectedApps.addAll(ids);}
        favoritesOnly.setOnCheckedChangeListener((b,c)->filter());
        selectionButton.setOnClickListener(v->{if(busy)return;selecting=!selecting;if(!selecting)selectedApps.clear();updateSelection();adapter.notifyDataSetChanged();});
        actionsButton.setOnClickListener(v->actions());updateSelection();
        if(getResources().getConfiguration().fontScale>=1.3f||getResources().getConfiguration().screenHeightDp<650){findViewById(R.id.home_heading).setVisibility(View.GONE);findViewById(R.id.home_description).setVisibility(View.GONE);}
        if(getResources().getConfiguration().fontScale>=1.3f){
            ((LinearLayout)findViewById(R.id.filter_summary)).setOrientation(LinearLayout.VERTICAL);
            count.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        }
        appList.setLayoutManager(new LinearLayoutManager(this));appList.setAdapter(adapter);
        access=new ViewModelProvider(this).get(AccessModel.class);
        rootCard.setOnClickListener(v->access.select(AccessGate.Mode.ROOT));shizukuCard.setOnClickListener(v->access.select(AccessGate.Mode.SHIZUKU));
        allow.setOnClickListener(v->access.connect(true));openShizuku.setOnClickListener(v->openShizuku());
        findViewById(R.id.setup_help).setOnClickListener(v->help());findViewById(R.id.about).setOnClickListener(v->about());
        statusButton.setOnClickListener(v->{if(!busy)new MaterialAlertDialogBuilder(this).setTitle("Connection")
            .setMessage("AppLingo is connected through "+(access.snapshot().mode==AccessGate.Mode.ROOT?"Root":"Shizuku")+".\n\nAndroid profile: "+userName(access.userId)+" (user "+access.userId+"). Access is checked again when you return to the app.")
            .setPositiveButton("Done",null).setNeutralButton("Change access method",(d,w)->access.setup()).setNegativeButton("Change profile",(d,w)->pickUser()).show();});
        search.addTextChangedListener(watcher(this::filter));allApps.setOnCheckedChangeListener((b,c)->filter());
        access.working.observe(this,this::setBusy);
        access.progress.observe(this,text->{TextView status=findViewById(R.id.operation_status);status.setText(text);status.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);});
        access.languages.observe(this,values->{knownLocales.putAll(values);adapter.notifyDataSetChanged();});
        access.completed.observe(this,result->{if(result!=null){access.acknowledge();showResult(result);}});
        access.state().observe(this,this::renderAccess);access.start();loadApps();
        Intent launch=getIntent();
        if(launch!=null){
            if("favorites".equals(launch.getStringExtra("filter")))favoritesOnly.setChecked(true);
            if("refresh".equals(launch.getStringExtra("action")))pendingRefresh=true;
        }
    }
    @Override protected void onResume(){super.onResume();refreshPhoneLanguage();if(access!=null){if(firstResume)firstResume=false;else access.resume();}}
    private String phoneLanguage(){
        return PhoneLanguages.describe(getSystemService(android.app.LocaleManager.class).getSystemLocales(),Locale.getDefault());
    }
    private String followSystem(){return getString(R.string.follow_system_language,phoneLanguage());}
    private void refreshPhoneLanguage(){
        if(home==null||setup==null)return;
        String text=getString(R.string.phone_language,phoneLanguage());
        ((TextView)home.findViewById(R.id.phone_language_home)).setText(text);
        ((TextView)setup.findViewById(R.id.phone_language_setup)).setText(text);
        adapter.notifyDataSetChanged();
    }
    void renderAccess(AccessGate.Snapshot state){
        boolean ready=state.phase==AccessGate.Phase.READY;
        if(!ready&&activeSheet!=null){activeSheet.dismiss();activeSheet=null;}
        if(android.animation.ValueAnimator.areAnimatorsEnabled()&&root.isLaidOut()&&home.getVisibility()!=(ready?View.VISIBLE:View.GONE)){
            MaterialFadeThrough fade=new MaterialFadeThrough();fade.setDuration(220);TransitionManager.beginDelayedTransition(root,fade);
        }
        home.setVisibility(ready?View.VISIBLE:View.GONE);setup.setVisibility(ready?View.GONE:View.VISIBLE);
        if(ready){statusButton.setText(state.message);return;}
        boolean checking=state.phase==AccessGate.Phase.CHECKING,isRoot=state.mode==AccessGate.Mode.ROOT;
        styleAccessCard(rootCard,isRoot);styleAccessCard(shizukuCard,!isRoot);
        rootCard.setEnabled(!checking);shizukuCard.setEnabled(!checking);allow.setEnabled(!checking);
        accessMessage.setText(state.message);accessProgress.setVisibility(checking?View.VISIBLE:View.GONE);
        allow.setText(checking?getString(R.string.checking):state.message.contains("Reconnect")||state.message.contains("stopped")?"Reconnect":getString(isRoot?R.string.allow_root:R.string.allow_shizuku));
        openShizuku.setVisibility(isRoot?View.GONE:View.VISIBLE);openShizuku.setEnabled(!checking);
    }
    private void styleAccessCard(MaterialCardView card,boolean selected){
        card.setCheckable(true);card.setChecked(selected);card.setStrokeWidth(dp(selected?2:1));
        card.setStrokeColor(color(selected?androidx.appcompat.R.attr.colorPrimary:com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(color(selected?com.google.android.material.R.attr.colorPrimaryContainer:com.google.android.material.R.attr.colorSurfaceContainerLow));
        int foreground=color(selected?com.google.android.material.R.attr.colorOnPrimaryContainer:com.google.android.material.R.attr.colorOnSurface);
        card.setCheckedIconTint(android.content.res.ColorStateList.valueOf(foreground));
        tintCard(card,foreground);
    }
    private void tintCard(ViewGroup group,int foreground){
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);
            if(child instanceof TextView)((TextView)child).setTextColor(foreground);
            else if(child instanceof ImageView)((ImageView)child).setImageTintList(android.content.res.ColorStateList.valueOf(foreground));
            else if(child instanceof ViewGroup)tintCard((ViewGroup)child,foreground);
        }
    }
    private void openShizuku(){
        Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if(launch!=null){try{startActivity(launch);}catch(Exception e){error("Couldn’t open Shizuku",e.getMessage());}}
        else new MaterialAlertDialogBuilder(this).setTitle("Get Shizuku").setMessage("Install Shizuku, follow its wireless debugging setup, then return here and allow access.")
            .setPositiveButton("Open setup guide",(d,w)->openUrl("https://shizuku.rikka.app/guide/setup/")).setNegativeButton(R.string.cancel,null).show();
    }
    private void help(){new MaterialAlertDialogBuilder(this).setTitle("AppLingo "+BuildConfig.VERSION_NAME)
        .setMessage("Choose Root if your phone is rooted. Otherwise, install and start Shizuku using wireless debugging, then allow AppLingo access.\n\nSearch by app name or package. Tap a star to pin a favorite. Select apps to apply a language in bulk. Actions includes Refresh shown languages and persistent Undo last change. Follow system removes an app’s language override.\n\nAndroid 13+ is required. AppLingo can request languages for apps outside Android’s language picker, but cannot add missing translations or override an app’s own language logic.\n\nChanges apply only to the selected Android profile. No app data is read or cleared. There are no ads, analytics or internet permission. Language overrides remain after uninstalling AppLingo.\n\nBuilt with Material 3 Expressive, AndroidX and Shizuku API. Open-source notices are included with the app.")
        .setPositiveButton(R.string.done,null).setNeutralButton(R.string.setup_help,(d,w)->openUrl("https://shizuku.rikka.app/guide/setup/")).show();}
    private void about(){new MaterialAlertDialogBuilder(this).setTitle("AppLingo "+BuildConfig.VERSION_NAME)
        .setMessage("Version "+BuildConfig.VERSION_NAME+" (versionCode "+BuildConfig.VERSION_CODE+").\n\nChange per-app language preferences on Android 13+ using Root or Shizuku. Free and open source under the MIT License.\n\nNo internet permission, no ads, no analytics. App data is never read or cleared.\n\nBuilt with Material 3 Expressive, AndroidX Lifecycle, and Shizuku API. Shizuku is © RikkaApps under its own license.")
        .setPositiveButton(R.string.done,null).setNeutralButton("View source",(d,w)->openUrl("https://github.com/jaydenchan1027/AppLingo")).show();}
    private String userName(int id){
        if(id==0)return "Owner";
        if(id==10)return "Work profile";
        return "User "+id;
    }
    /** Parse `cmd user list` output like "UserInfo{0:Owner:13} running" into id→name. */
    private Map<Integer,String> parseUsers(String raw){
        Map<Integer,String> users=new LinkedHashMap<>();
        if(raw!=null)for(String line:raw.split("\\n")){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("UserInfo\\{(\\d+):([^:}]+):").matcher(line);
            if(m.find()){try{users.put(Integer.parseInt(m.group(1)),m.group(2).trim());}catch(Exception ignored){}}
        }
        users.putIfAbsent(access.userId,userName(access.userId));
        return users;
    }
    private void pickUser(){
        if(busy)return;
        setBusy(true);
        access.listUsers(new AccessModel.Callback(){
            public void done(String value){if(isDestroyed())return;setBusy(false);showUserPicker(value);}
            public void failed(String message){if(isDestroyed())return;setBusy(false);showUserPicker("");}
        });
    }
    private void showUserPicker(String raw){
        final Map<Integer,String> users=parseUsers(raw);
        final ArrayList<Integer> ids=new ArrayList<>(users.keySet());
        ArrayList<String> labels=new ArrayList<>();
        for(int id:ids)labels.add(users.get(id)+" (user "+id+")");
        labels.add("Custom user ID…");
        new MaterialAlertDialogBuilder(this).setTitle("Choose Android profile")
            .setMessage("Language commands target this profile. The app list shows the current user’s apps; for other profiles you can still change languages by package name.")
            .setItems(labels.toArray(new String[0]),(d,which)->{
                if(which<ids.size())switchUser(ids.get(which));
                else customUser();
            }).setNegativeButton(R.string.cancel,null).show();
    }
    private void customUser(){
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);field.setHint("User ID (for example 0, 10)");
        TextInputEditText input=new TextInputEditText(field.getContext());input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.addView(input);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(12),dp(24),0);box.addView(field,full());
        new MaterialAlertDialogBuilder(this).setTitle("Custom profile").setView(box).setNegativeButton(R.string.cancel,null).setPositiveButton("Use",(d,w)->{
            try{int id=Integer.parseInt(input.getText()==null?"":input.getText().toString().trim());if(id<0)throw new NumberFormatException();switchUser(id);}
            catch(Exception e){error("Invalid user ID","Enter a non-negative number, such as 0 for the owner or 10 for a work profile.");}
        }).show();
    }
    private void switchUser(int id){
        if(id==access.userId)return;
        access.setUserId(id);
        knownLocales.clear();access.languages.setValue(new HashMap<>());
        Snackbar.make(root,"Profile changed to "+userName(id)+" (user "+id+").",Snackbar.LENGTH_LONG).show();
    }
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){error("No browser available","Install a browser to open the setup guide.");}}
    private void loadApps(){
        loader.execute(()->{
            ArrayList<AppEntry> found=new ArrayList<>();
            try{
                PackageManager pm=getPackageManager();
                for(ApplicationInfo info:pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))){
                    if(!info.packageName.equals(getPackageName()))found.add(new AppEntry(info.packageName,info.loadLabel(pm).toString(),pm.getLaunchIntentForPackage(info.packageName)!=null,info));
                }
                found.sort(Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));
                runOnUiThread(()->{if(isDestroyed())return;apps.clear();apps.addAll(found);loading=false;filter();
                    if(pendingRefresh&&access.snapshot().phase==AccessGate.Phase.READY){pendingRefresh=false;ArrayList<String> pkgs=new ArrayList<>();for(AppEntry a:shown)pkgs.add(a.pkg);access.refreshLanguages(pkgs);}
                });
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;loading=false;count.setText("Couldn’t load apps");empty.setText("Close and reopen AppLingo to try again.");empty.setVisibility(View.VISIBLE);});}
        });
    }
    private void filter(){
        if(search==null)return;String q=search.getText()==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);shown.clear();
        for(AppEntry app:apps)if((!favoritesOnly.isChecked()||favorites.contains(app.pkg))&&(allApps.isChecked()||app.launchable)&&(app.label.toLowerCase(Locale.ROOT).contains(q)||app.pkg.toLowerCase(Locale.ROOT).contains(q)))shown.add(app);
        shown.sort(Comparator.<AppEntry,Boolean>comparing(a->!favorites.contains(a.pkg)).thenComparing(a->a.label.toLowerCase(Locale.ROOT)));
        adapter.notifyDataSetChanged();count.setText(loading?getString(R.string.loading_apps):getResources().getQuantityString(R.plurals.app_count,shown.size(),shown.size()));
        empty.setVisibility(!loading&&shown.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void selectApp(AppEntry app){
        if(busy||access.snapshot().phase!=AccessGate.Phase.READY)return;hideKeyboard();setBusy(true);
        access.locale(app.pkg,"",false,new AccessModel.Callback(){
            public void done(String value){if(isDestroyed())return;setBusy(false);knownLocales.put(app.pkg,value);adapter.notifyDataSetChanged();loadCompatibility(app,value);}
            public void failed(String message){if(isDestroyed())return;setBusy(false);error("Couldn’t read language",message);}
        });
    }
    private void edit(AppEntry app,String current){
        BottomSheetDialog sheet=new BottomSheetDialog(this);activeSheet=sheet;
        sheet.setOnDismissListener(d->{if(activeSheet==sheet)activeSheet=null;});
        LinearLayout content=sheetContent();
        ImageView icon=new ImageView(this);icon.setImageDrawable(app.info.loadIcon(getPackageManager()));LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(56),dp(56));iconSize.bottomMargin=dp(12);content.addView(icon,iconSize);
        content.addView(label(app.label,com.google.android.material.R.attr.textAppearanceHeadlineMedium));
        TextView pkg=label(app.pkg,com.google.android.material.R.attr.textAppearanceBodySmall);pkg.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));content.addView(pkg);
        TextView currentText=label(getString(R.string.current_language,current.isEmpty()?followSystem():display(current)),com.google.android.material.R.attr.textAppearanceBodyMedium);space(currentText,16,12);content.addView(currentText);
        MaterialButton pin=button(favorites.contains(app.pkg)?"Remove from favorites":"Add to favorites",false);
        pin.setOnClickListener(v->{toggleFavorite(app);pin.setText(favorites.contains(app.pkg)?"Remove from favorites":"Add to favorites");});content.addView(pin,full());
        TextView compatibility=label(editorSupported.isEmpty()?"Supported languages: unknown. This app has not declared a readable language list.":"Declared languages: "+joinLanguages(editorSupported),com.google.android.material.R.attr.textAppearanceBodyMedium);space(compatibility,12,8);content.addView(compatibility);
        final String[] selected={current};
        MaterialButton choice=button(current.isEmpty()?getString(R.string.choose_language):display(current),false);choice.setIconResource(R.drawable.ic_chevron);choice.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);content.addView(choice,full());
        choice.setOnClickListener(v->languagePicker(tag->{selected[0]=tag;choice.setText(display(tag));},sheet));
        MaterialButton custom=new MaterialButton(this,null,androidx.appcompat.R.attr.borderlessButtonStyle);custom.setText(R.string.custom_tag);custom.setMinHeight(dp(52));content.addView(custom,full());
        custom.setOnClickListener(v->customTag(tag->{selected[0]=tag;choice.setText(display(tag));}));
        TextView note=label(getString(R.string.language_note),com.google.android.material.R.attr.textAppearanceBodyMedium);space(note,8,16);content.addView(note);
        MaterialButton apply=button(getString(R.string.apply),true);apply.setMinHeight(dp(60));content.addView(apply,full());
        apply.setOnClickListener(v->{if(selected[0].isEmpty()){error("Choose a language first","Select a language before applying.");return;}sheet.dismiss();activeSheet=null;confirmCompatibility(app,selected[0]);});
        MaterialButton reset=button(followSystem(),false);content.addView(reset,full());reset.setOnClickListener(v->{sheet.dismiss();activeSheet=null;apply(app,"");});
        ScrollView scroll=new ScrollView(this);scroll.addView(content);sheet.setContentView(scroll);showSheet(sheet);
    }
    private interface Choice{void accept(String tag);}
    private void customTag(Choice choice){
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);field.setHint(R.string.custom_tag);field.setHelperText(getString(R.string.custom_tag_hint));
        TextInputEditText input=new TextInputEditText(field.getContext());input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.addView(input);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(12),dp(24),0);box.addView(field,full());
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_tag_title).setView(box).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.use_tag,null).create();
        dialog.setOnShowListener(d->dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String tag=LocaleCommands.normalize(input.getText()==null?"":input.getText().toString().trim());if(tag.isEmpty())throw new IllegalArgumentException("Enter a language tag.");choice.accept(tag);dialog.dismiss();}
            catch(Exception e){field.setError(e.getMessage());}
        }));dialog.show();
    }
    private void languagePicker(Choice callback,BottomSheetDialog parent){
        LinkedHashMap<String,String> options=new LinkedHashMap<>();
        for(String tag:editorSupported)options.put(tag,display(tag)+" · declared by app");
        for(String tag:new String[]{"en","en-GB","zh-Hant-HK","zh-Hant-TW","zh-Hans-CN","ja","ko","fr","de","es","pt-BR","ar","hi","id","th","vi"})options.putIfAbsent(tag,display(tag));
        ArrayList<Locale> locales=new ArrayList<>(Arrays.asList(Locale.getAvailableLocales()));locales.sort(Comparator.comparing(l->l.getDisplayName(Locale.getDefault())));
        for(Locale locale:locales)if(!locale.getLanguage().isEmpty()&&!"und".equals(locale.toLanguageTag()))options.putIfAbsent(locale.toLanguageTag(),display(locale.toLanguageTag()));
        BottomSheetDialog sheet=new BottomSheetDialog(this);activeSheet=sheet;
        LinearLayout content=sheetContent();content.addView(label(getString(R.string.choose_language),com.google.android.material.R.attr.textAppearanceHeadlineMedium));
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);field.setHint(R.string.search_languages);field.setStartIconDrawable(R.drawable.ic_search);field.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText query=new TextInputEditText(field.getContext());query.setSingleLine(true);field.addView(query);space(field,16,8);content.addView(field,full());
        ListView list=new ListView(this);list.setDividerHeight(0);int height=Math.max(dp(160),(int)(getResources().getDisplayMetrics().heightPixels*.46));content.addView(list,new LinearLayout.LayoutParams(-1,height));
        ArrayList<String> tags=new ArrayList<>(),labels=new ArrayList<>();ArrayAdapter<String> choices=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels);list.setAdapter(choices);
        Runnable update=()->{String q=query.getText()==null?"":query.getText().toString().toLowerCase(Locale.ROOT);tags.clear();labels.clear();for(Map.Entry<String,String> entry:options.entrySet())if(entry.getValue().toLowerCase(Locale.ROOT).contains(q)){tags.add(entry.getKey());labels.add(entry.getValue());}choices.notifyDataSetChanged();};
        query.addTextChangedListener(watcher(update));update.run();list.setOnItemClickListener((p,v,pos,id)->{callback.accept(tags.get(pos));sheet.dismiss();});
        sheet.setOnDismissListener(d->{activeSheet=parent;if(parent!=null&&access.snapshot().phase!=AccessGate.Phase.READY)parent.dismiss();});
        sheet.setContentView(content);showSheet(sheet);
    }
    private void apply(AppEntry app,String tag){
        Map<String,String> requests=new LinkedHashMap<>();requests.put(app.pkg,tag);confirmChanges(requests);
    }
    private void confirmChanges(Map<String,String> requests){
        if(busy||requests.isEmpty()||access.snapshot().phase!=AccessGate.Phase.READY)return;
        String language=requests.values().iterator().next();
        new MaterialAlertDialogBuilder(this).setTitle("Change "+requests.size()+" app"+(requests.size()==1?"":"s")+"?")
            .setMessage((language.isEmpty()?followSystem():display(language))+"\n\nSome apps may need reopening. Missing translations cannot be added."+(access.canUndo()?"\n\nThis replaces your previous undo history.":""))
            .setNegativeButton(R.string.cancel,null).setPositiveButton("Apply",(d,w)->access.change(requests,false)).show();
    }
    private void showResult(ChangeEngine.Result result){
        selectedApps.clear();selecting=false;updateSelection();adapter.notifyDataSetChanged();
        String message=result.changed+" app"+(result.changed==1?"":"s")+" changed. "+result.unchanged+" already matched.";
        if(!result.issues.isEmpty())message+="\n\n"+String.join("\n",result.issues);
        message+="\n\nIf an app still shows its old language, close and reopen it. Undo is available under Actions.";
        MaterialAlertDialogBuilder dialog=new MaterialAlertDialogBuilder(this).setTitle(result.interrupted?"Stopped before completion":"Languages checked").setMessage(message).setPositiveButton(R.string.done,null);
        if(result.values.size()==1){String pkg=result.values.keySet().iterator().next();Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null)dialog.setNeutralButton(R.string.open_app,(d,w)->{try{startActivity(launch);}catch(Exception e){error("Couldn’t open app",e.getMessage());}});}
        if(access.canUndo())dialog.setNegativeButton("Undo…",(d,w)->confirmUndo());dialog.show();
    }
    private void confirmUndo(){
        if(busy)return;
        if(access.snapshot().phase!=AccessGate.Phase.READY){error("Reconnect first","Your undo history is saved. Reconnect, then open Actions → Undo last change.");return;}
        new MaterialAlertDialogBuilder(this).setTitle("Undo last change?").setMessage("Restore each app’s previous language. Apps changed elsewhere will be skipped to protect their newer settings.").setNegativeButton(R.string.cancel,null).setPositiveButton("Restore",(d,w)->access.change(Collections.emptyMap(),true)).show();
    }
    private void actions(){
        if(busy)return;
        String[] items={"Change selected ("+selectedApps.size()+")","Select all shown ("+shown.size()+")","Clear selection","Refresh shown languages","Undo last change","Export favorites ("+favorites.size()+")","Import favorites"};
        new MaterialAlertDialogBuilder(this).setTitle("App actions").setItems(items,(d,which)->{
            if(which==0){if(selectedApps.isEmpty()){error("Select apps first","Tap Select apps, then choose the apps to change.");return;}
                editorSupported=Collections.emptyList();
                new MaterialAlertDialogBuilder(this).setTitle("Language for "+selectedApps.size()+" apps").setMessage("Each app must include the selected translation. Bulk changes stop on the first error; completed changes can be undone.")
                    .setPositiveButton("Choose language",(a,b)->languagePicker(tag->bulkApply(tag),null))
                    .setNeutralButton("Follow system",(a,b)->bulkApply("")).setNegativeButton(R.string.cancel,null).show();
            }else if(which==1){selecting=true;for(AppEntry app:shown)selectedApps.add(app.pkg);updateSelection();adapter.notifyDataSetChanged();}
            else if(which==2){selectedApps.clear();selecting=false;updateSelection();adapter.notifyDataSetChanged();}
            else if(which==3){ArrayList<String> packages=new ArrayList<>();for(AppEntry app:shown)packages.add(app.pkg);access.refreshLanguages(packages);}
            else if(which==4){if(access.canUndo())confirmUndo();else error("Nothing to undo","Your next language change will save its previous settings here.");}
            else if(which==5)exportFavorites();
            else importFavorites();
        }).show();
    }
    private void exportFavorites(){
        if(favorites.isEmpty()){error("No favorites","Star some apps first, then export.");return;}
        StringBuilder sb=new StringBuilder();
        for(String pkg:new TreeSet<>(favorites))sb.append(pkg).append("\n");
        String data=sb.toString().trim();
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AppLingo favorites",data));}
        new MaterialAlertDialogBuilder(this).setTitle("Favorites exported")
            .setMessage(favorites.size()+" package(s) copied to the clipboard.\n\n"+data)
            .setPositiveButton(R.string.done,null).show();
    }
    private void importFavorites(){
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);field.setHint("Paste package names, one per line");
        TextInputEditText input=new TextInputEditText(field.getContext());input.setSingleLine(false);input.setMinLines(4);field.addView(input);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(12),dp(24),0);box.addView(field,full());
        new MaterialAlertDialogBuilder(this).setTitle("Import favorites").setView(box).setNegativeButton(R.string.cancel,null).setPositiveButton("Import",(d,w)->{
            String text=input.getText()==null?"":input.getText().toString();
            int added=0,skipped=0;
            for(String line:text.split("\\s+")){
                String pkg=line.trim();
                if(pkg.isEmpty())continue;
                if(!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")){skipped++;continue;}
                if(favorites.add(pkg))added++;
            }
            uiPrefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();
            filter();
            Snackbar.make(root,added+" favorite(s) added"+(skipped>0?", "+skipped+" skipped as invalid":"")+".",Snackbar.LENGTH_LONG).show();
        }).show();
    }
    private void bulkApply(String tag){Map<String,String> requests=new LinkedHashMap<>();for(String pkg:selectedApps)requests.put(pkg,tag);confirmChanges(requests);}
    private void updateSelection(){selectionButton.setText(selecting?"Done · "+selectedApps.size():"Select apps");}
    private void toggleFavorite(AppEntry app){if(favorites.contains(app.pkg))favorites.remove(app.pkg);else favorites.add(app.pkg);uiPrefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();filter();}
    private void toggleSelected(AppEntry app){if(busy)return;if(!selectedApps.add(app.pkg))selectedApps.remove(app.pkg);updateSelection();adapter.notifyDataSetChanged();}
    private void loadCompatibility(AppEntry app,String current){
        setBusy(true);loader.execute(()->{List<String> tags=new ArrayList<>();
            try{android.app.LocaleConfig config=new android.app.LocaleConfig(createPackageContext(app.pkg,0));android.os.LocaleList locales=config.getSupportedLocales();if(config.getStatus()==android.app.LocaleConfig.STATUS_SUCCESS&&locales!=null)for(int i=0;i<locales.size();i++)tags.add(locales.get(i).toLanguageTag());}catch(Exception ignored){}
            runOnUiThread(()->{if(isDestroyed())return;setBusy(false);if(access.snapshot().phase!=AccessGate.Phase.READY)return;editorSupported=tags;edit(app,current);});
        });
    }
    private String joinLanguages(List<String> tags){ArrayList<String> names=new ArrayList<>();for(String tag:tags.subList(0,Math.min(6,tags.size())))names.add(display(tag));return String.join(", ",names)+(tags.size()>6?"; "+(tags.size()-6)+" more listed first in the picker.":"");}
    private void confirmCompatibility(AppEntry app,String tag){
        if(!editorSupported.isEmpty()&&!editorSupported.contains(tag))new MaterialAlertDialogBuilder(this).setTitle("Language not declared").setMessage("This exact language is not in the app’s declared list. It may use a related language or keep its current language.").setNegativeButton(R.string.cancel,null).setPositiveButton("Try anyway",(d,w)->apply(app,tag)).show();
        else apply(app,tag);
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("selecting",selecting);out.putStringArrayList("selected",new ArrayList<>(selectedApps));super.onSaveInstanceState(out);}
    private void showSheet(BottomSheetDialog sheet){
        // MaterialBackOrchestrator owns predictive progress/cancel/commit for this dialog.
        // Leave the activity's root back dispatcher unconsumed for the OS home preview.

        sheet.setOnShowListener(d->{FrameLayout frame=sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);if(frame!=null){BottomSheetBehavior<FrameLayout> b=BottomSheetBehavior.from(frame);b.setSkipCollapsed(true);b.setState(BottomSheetBehavior.STATE_EXPANDED);}});sheet.show();
    }
    private LinearLayout sheetContent(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(24),dp(28),dp(24),dp(24));return l;}
    private TextView label(String text,int appearance){TextView v=new TextView(this);android.util.TypedValue value=new android.util.TypedValue();getTheme().resolveAttribute(appearance,value,true);v.setTextAppearance(value.resourceId);v.setText(text);return v;}
    private MaterialButton button(String text,boolean filled){MaterialButton b=new MaterialButton(this,null,filled?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonTonalStyle);b.setText(text);b.setMinHeight(dp(52));return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private void space(View v,int top,int bottom){LinearLayout.LayoutParams p=full();p.topMargin=dp(top);p.bottomMargin=dp(bottom);v.setLayoutParams(p);}
    private int color(int attr){return MaterialColors.getColor(this,attr,Color.BLACK);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String display(String tags){String tag=tags.split(",")[0];return Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())+" · "+tags;}
    private void setBusy(boolean value){busy=value;actionProgress.setVisibility(value?View.VISIBLE:View.GONE);statusButton.setEnabled(!value);appList.setEnabled(!value);selectionButton.setEnabled(!value);actionsButton.setEnabled(!value);}
    private void error(String title,String message){if(!isDestroyed())new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message==null?"Please try again.":message).setPositiveButton(R.string.done,null).show();}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
    private TextWatcher watcher(Runnable action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){action.run();}public void afterTextChanged(Editable e){}};}
    static final class AppEntry{final String pkg,label;final boolean launchable;final ApplicationInfo info;AppEntry(String p,String l,boolean b,ApplicationInfo i){pkg=p;label=l;launchable=b;info=i;}}
    private static final class AppHolder extends RecyclerView.ViewHolder{final ImageView icon;final TextView title,detail;final MaterialButton favorite;final android.widget.CheckBox selected;AppHolder(View v){super(v);icon=v.findViewById(R.id.app_icon);title=v.findViewById(R.id.app_label);detail=v.findViewById(R.id.app_detail);favorite=v.findViewById(R.id.app_favorite);selected=v.findViewById(R.id.app_selected);}}
    private final class AppAdapter extends RecyclerView.Adapter<AppHolder>{
        public int getItemCount(){return shown.size();}
        @Override public AppHolder onCreateViewHolder(ViewGroup parent,int type){return new AppHolder(getLayoutInflater().inflate(R.layout.item_app,parent,false));}
        @Override public void onBindViewHolder(AppHolder h,int position){AppEntry app=shown.get(position);h.icon.setImageDrawable(app.info.loadIcon(getPackageManager()));h.title.setText(app.label);String saved=knownLocales.get(app.pkg);h.detail.setText(saved==null?"Language not checked · tap to check":"Last checked: "+(saved.isEmpty()?followSystem():display(saved)));h.favorite.setIconResource(favorites.contains(app.pkg)?R.drawable.ic_star:R.drawable.ic_star_outline);h.favorite.setContentDescription((favorites.contains(app.pkg)?"Remove from favorites: ":"Add to favorites: ")+app.label);h.favorite.setOnClickListener(v->{if(!busy)toggleFavorite(app);});
            h.selected.setOnCheckedChangeListener(null);h.selected.setVisibility(selecting?View.VISIBLE:View.GONE);h.selected.setChecked(selectedApps.contains(app.pkg));h.selected.setContentDescription("Select "+app.label);h.selected.setEnabled(!busy);h.selected.setOnClickListener(v->toggleSelected(app));
            h.itemView.setOnClickListener(v->{if(selecting)toggleSelected(app);else selectApp(app);});
            h.itemView.setOnLongClickListener(v->{if(busy)return false;selecting=true;toggleSelected(app);return true;});}
    }
    @Override protected void onDestroy(){if(activeSheet!=null)activeSheet.dismiss();loader.shutdownNow();super.onDestroy();}
}
