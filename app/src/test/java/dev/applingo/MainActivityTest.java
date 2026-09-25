package dev.applingo;
import android.graphics.*;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
 private ActivityController<MainActivity> controller;
 private MainActivity activity;
 @Before public void launch(){ApplicationProvider.getApplicationContext().getSharedPreferences("access",0).edit().clear().commit();
  android.content.Context ctx=ApplicationProvider.getApplicationContext();
  ctx.getSharedPreferences("ui",0).edit().clear().commit();ctx.getSharedPreferences("changes",0).edit().clear().commit();
  for(String name:new String[]{"Camera","Calendar","Clock","Music","Notes"}){
   android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName="dev.preview."+name.toLowerCase(java.util.Locale.ROOT);info.versionName="1";info.applicationInfo=new android.content.pm.ApplicationInfo();info.applicationInfo.packageName=info.packageName;info.applicationInfo.nonLocalizedLabel=name;info.applicationInfo.flags=android.content.pm.ApplicationInfo.FLAG_INSTALLED;
   shadowOf(ctx.getPackageManager()).installPackage(info);
  }
  controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();shadowOf(Looper.getMainLooper()).idle();}
 @After public void close(){controller.pause().stop().destroy();}
 @Test public void noPermissionShowsWelcome(){assertEquals(View.VISIBLE,activity.findViewById(R.id.setup_screen).getVisibility());assertEquals(View.GONE,activity.findViewById(R.id.home_screen).getVisibility());capture("setup-light.png");}
 @Test public void choosingRootChangesActionWithoutGranting(){activity.findViewById(R.id.choose_root).performClick();assertEquals("Allow root access",((TextView)activity.findViewById(R.id.allow_access)).getText().toString());assertEquals(View.GONE,activity.findViewById(R.id.home_screen).getVisibility());}
 @Test public void checkingDisablesRepeatedRequests(){activity.renderAccess(new AccessGate.Snapshot(AccessGate.Mode.ROOT,AccessGate.Phase.CHECKING,"Checking root access…"));assertFalse(activity.findViewById(R.id.allow_access).isEnabled());assertFalse(activity.findViewById(R.id.choose_shizuku).isEnabled());}
 @Test public void validAccessOpensHomeAndLossReturnsToSetup(){activity.renderAccess(new AccessGate.Snapshot(AccessGate.Mode.SHIZUKU,AccessGate.Phase.READY,"Shizuku connected"));assertEquals(View.VISIBLE,activity.findViewById(R.id.home_screen).getVisibility());((android.widget.CompoundButton)activity.findViewById(R.id.show_all)).setChecked(true);awaitApps();capture("home-light.png");activity.renderAccess(new AccessGate.Snapshot(AccessGate.Mode.SHIZUKU,AccessGate.Phase.SETUP,"Shizuku stopped"));assertEquals(View.VISIBLE,activity.findViewById(R.id.setup_screen).getVisibility());assertEquals(View.GONE,activity.findViewById(R.id.home_screen).getVisibility());}
 @Test @Config(qualifiers="w412dp-h915dp-night-mdpi") public void darkThemeRenders(){capture("setup-dark.png");activity.renderAccess(new AccessGate.Snapshot(AccessGate.Mode.ROOT,AccessGate.Phase.READY,"Root connected"));((android.widget.CompoundButton)activity.findViewById(R.id.show_all)).setChecked(true);awaitApps();capture("home-dark.png");}
 @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void compactSetupKeepsScrollableActions(){assertTrue(activity.findViewById(R.id.setup_screen) instanceof android.widget.ScrollView);capture("setup-compact.png");}
 @Test public void installedAppSearchFiltersResults(){
  ((android.widget.CompoundButton)activity.findViewById(R.id.show_all)).setChecked(true);awaitApps();
  ((android.widget.EditText)activity.findViewById(R.id.search_apps)).setText("camera");
  assertEquals(1,((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.app_list)).getAdapter().getItemCount());
  ((android.widget.EditText)activity.findViewById(R.id.search_apps)).setText("no-such-installed-app");
  assertEquals(0,((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.app_list)).getAdapter().getItemCount());
 }
 @Test public void languageEditorAndPickerRender() throws Exception {
  AccessModel model=org.robolectric.util.ReflectionHelpers.getField(activity,"access");
  AccessGate gate=org.robolectric.util.ReflectionHelpers.getField(model,"gate");gate.ready(gate.begin("Test connection"));
  activity.renderAccess(gate.snapshot());
  android.content.pm.ApplicationInfo info=activity.getPackageManager().getApplicationInfo("dev.preview.camera",0);
  MainActivity.AppEntry app=new MainActivity.AppEntry(info.packageName,"Camera",true,info);
  org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"edit",org.robolectric.util.ReflectionHelpers.ClassParameter.from(MainActivity.AppEntry.class,app),org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,"en"));
  com.google.android.material.bottomsheet.BottomSheetDialog sheet=org.robolectric.util.ReflectionHelpers.getField(activity,"activeSheet");
  assertTrue(sheet.isShowing());captureView(sheet.getWindow().getDecorView(),"language-editor.png");
  View choose=findText(sheet.getWindow().getDecorView(),"English · en");assertNotNull(choose);choose.performClick();
  com.google.android.material.bottomsheet.BottomSheetDialog picker=org.robolectric.util.ReflectionHelpers.getField(activity,"activeSheet");
  assertNotSame(sheet,picker);assertTrue(picker.isShowing());captureView(picker.getWindow().getDecorView(),"language-picker.png");
  com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior=picker.getBehavior();
  androidx.activity.BackEventCompat start=new androidx.activity.BackEventCompat(0,300,0,androidx.activity.BackEventCompat.EDGE_LEFT);
  androidx.activity.BackEventCompat progress=new androidx.activity.BackEventCompat(100,300,.6f,androidx.activity.BackEventCompat.EDGE_LEFT);
  behavior.startBackProgress(start);behavior.updateBackProgress(progress);behavior.cancelBackProgress();
  shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
  assertTrue(picker.isShowing());assertTrue(sheet.isShowing());
  behavior.startBackProgress(start);behavior.updateBackProgress(progress);behavior.handleBackInvoked();
  shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
  assertFalse(picker.isShowing());assertTrue(sheet.isShowing());
  assertSame(sheet,org.robolectric.util.ReflectionHelpers.getField(activity,"activeSheet"));
  sheet.dismiss();
 }
 @Test public void phoneLanguagesUseSystemSettingAndRefreshOnReturn(){
  android.content.res.Resources system=android.content.res.Resources.getSystem();
  android.content.res.Configuration original=new android.content.res.Configuration(system.getConfiguration());
  java.util.Locale oldDefault=java.util.Locale.getDefault();
  try {
   android.content.res.Configuration config=new android.content.res.Configuration(original);
   config.setLocales(android.os.LocaleList.forLanguageTags("fr-CA,en-US"));
   system.updateConfiguration(config,system.getDisplayMetrics());
   java.util.Locale.setDefault(java.util.Locale.JAPANESE);
   activity.getSystemService(android.app.LocaleManager.class).setApplicationLocales(android.os.LocaleList.forLanguageTags("ja"));
   controller.pause().resume();
   String text=((TextView)activity.findViewById(R.id.phone_language_home)).getText().toString();
   assertTrue(text.contains("fr-CA"));assertTrue(text.contains("en-US"));assertFalse(text.contains("· ja"));
   assertTrue(text.indexOf("fr-CA")<text.indexOf("en-US"));
   String follow=org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"followSystem");
   assertTrue(follow.startsWith("Follow system"));assertTrue(follow.contains("fr-CA"));
   assertEquals(text,((TextView)activity.findViewById(R.id.phone_language_setup)).getText().toString());
   config.setLocales(android.os.LocaleList.forLanguageTags("de-DE"));system.updateConfiguration(config,system.getDisplayMetrics());
   controller.pause().resume();
   text=((TextView)activity.findViewById(R.id.phone_language_home)).getText().toString();
   assertTrue(text.contains("de-DE"));assertFalse(text.contains("fr-CA"));
  } finally {system.updateConfiguration(original,system.getDisplayMetrics());java.util.Locale.setDefault(oldDefault);}
 }
 @Test public void rootBackHasNoInterception(){assertFalse(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());}
 @Test public void favoritesPersistAndFilter() throws Exception {
  ((android.widget.CompoundButton)activity.findViewById(R.id.show_all)).setChecked(true);awaitApps();
  android.content.pm.ApplicationInfo info=activity.getPackageManager().getApplicationInfo("dev.preview.camera",0);
  MainActivity.AppEntry app=new MainActivity.AppEntry(info.packageName,"Camera",true,info);
  org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"toggleFavorite",org.robolectric.util.ReflectionHelpers.ClassParameter.from(MainActivity.AppEntry.class,app));
  assertTrue(activity.getSharedPreferences("ui",0).getStringSet("favorites",java.util.Collections.emptySet()).contains(info.packageName));
  ((android.widget.CompoundButton)activity.findViewById(R.id.favorites_only)).setChecked(true);
  assertEquals(1,((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.app_list)).getAdapter().getItemCount());
 }
 @Test public void selectionSurvivesRecreation() throws Exception {
  android.content.pm.ApplicationInfo info=activity.getPackageManager().getApplicationInfo("dev.preview.camera",0);
  MainActivity.AppEntry app=new MainActivity.AppEntry(info.packageName,"Camera",true,info);
  activity.findViewById(R.id.select_apps).performClick();
  org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"toggleSelected",org.robolectric.util.ReflectionHelpers.ClassParameter.from(MainActivity.AppEntry.class,app));
  controller.recreate();activity=controller.get();
  assertTrue(((TextView)activity.findViewById(R.id.select_apps)).getText().toString().contains("1"));
 }
 @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void largeTextKeepsListUsable(){
  android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());config.fontScale=2f;
  activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());controller.recreate();activity=controller.get();
  activity.renderAccess(new AccessGate.Snapshot(AccessGate.Mode.ROOT,AccessGate.Phase.READY,"Root connected"));
  ((android.widget.CompoundButton)activity.findViewById(R.id.show_all)).setChecked(true);awaitApps();capture("home-large-text.png");
  assertEquals(View.GONE,activity.findViewById(R.id.home_heading).getVisibility());
  assertTrue(activity.findViewById(R.id.app_list).getHeight()>100);
  assertEquals(1,((TextView)activity.findViewById(R.id.app_count)).getLineCount());
 }
 @Test public void journalSurvivesNewStoreInstance() throws Exception {
  android.content.SharedPreferences prefs=activity.getSharedPreferences("changes",0);UndoStore store=new UndoStore(prefs);
  java.util.Map<String,ChangeEngine.Entry> entries=new java.util.LinkedHashMap<>();entries.put("dev.preview.camera",new ChangeEngine.Entry("","fr"));store.save(entries);
  ChangeEngine.Entry restored=new UndoStore(prefs).load().get("dev.preview.camera");assertEquals("",restored.before);assertEquals("fr",restored.after);
 }
 private View findText(View v,String text){if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private void awaitApps(){for(int i=0;i<100;i++){shadowOf(Looper.getMainLooper()).idle();if(((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.app_list)).getAdapter().getItemCount()>=5)return;try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}throw new AssertionError("Fixture apps did not load");}
 private void capture(String name){captureView(activity.getWindow().getDecorView(),name);}
 private void captureView(View view,String name){
  int w=activity.getResources().getDisplayMetrics().widthPixels,h=activity.getResources().getDisplayMetrics().heightPixels;
  view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
  shadowOf(Looper.getMainLooper()).idle();
  view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
  androidx.transition.TransitionManager.endTransitions((android.view.ViewGroup)activity.findViewById(android.R.id.content));
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
  File out=new File("build/ui-previews",name);out.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}catch(Exception e){throw new AssertionError(e);}
 }
}
