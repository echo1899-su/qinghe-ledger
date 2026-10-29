package local.qinghe.ledger;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.print.*;
import android.text.InputType;
import android.util.*;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class MainActivity extends Activity {
 static final String ORIGIN="appassets.androidplatform.net";
 WebView web;boolean pdfBusy=false;FrameLayout root;SharedPreferences prefs;AtomicFile state;boolean ready=false,locked=false;long backgroundAt=0;String quick="";byte[] exportBytes;ValueCallback<Uri[]> fileCallback;View lockView;ChoiceView choiceView;View splash;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("local_settings",MODE_PRIVATE);state=new AtomicFile(new File(getFilesDir(),"ledger.json"));quick=getIntent().getStringExtra("quick");root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(246,247,242));setContentView(root);if(hasPin())getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()),ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return WindowInsets.CONSUMED;});}else root.setFitsSystemWindows(true);
  if(hasPin())unlock(true);else startWeb();scheduleReminder(prefs.getString("reminder",""));
 }
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);quick=intent.getStringExtra("quick");if(ready&&!locked)dispatchQuick();}
 void startWeb(){if(web!=null){web.setVisibility(View.VISIBLE);locked=false;return;}web=new WebView(this);web.setBackgroundColor(Color.rgb(246,247,242));WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setBlockNetworkLoads(true);settings.setSupportMultipleWindows(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.setWebViewClient(localClient());web.addJavascriptInterface(new Bridge(),"Android");
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{startActivityForResult(i,103);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("无法打开图片选择器");}return true;}});
  root.addView(web,new FrameLayout.LayoutParams(-1,-1));showSplash();web.loadUrl("https://"+ORIGIN+"/assets/index.html");locked=false;
 }
 WebViewClient localClient(){return new WebViewClient(){
  @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !isLocal(r.getUrl());}
  @Override public boolean shouldOverrideUrlLoading(WebView w,String url){return !isLocal(Uri.parse(url));}
  @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){Uri u=request.getUrl();try{if(!isLocal(u))return blocked();String path=u.getPath();if(path==null||!path.startsWith("/assets/")||path.contains(".."))return blocked();String file=path.substring(8);String mime=file.endsWith(".html")?"text/html":file.endsWith(".css")?"text/css":file.endsWith(".js")?"application/javascript":"application/octet-stream";return new WebResourceResponse(mime,"UTF-8",getAssets().open(file));}catch(Exception e){return blocked();}}
  @Override public void onPageFinished(WebView w,String url){if(w==web){ready=true;dispatchQuick();if(splash!=null)hideSplash();}}
 };}
 static boolean isLocal(Uri u){return "https".equals(u.getScheme())&&ORIGIN.equals(u.getHost());}
 static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Offline",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 void dispatchQuick(){if(quick!=null&&!quick.isEmpty()){String type=quick;quick="";js("App.quickAdd("+JSONObject.quote(type)+")");}}
 void js(String source){runOnUiThread(()->{if(web!=null)web.evaluateJavascript(source,null);});}
 void toast(String text){runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
 /** 入场：按当前主题铺一层柔和渐变，叶子淡入并轻微放大，加载完再淡出 */
 void showSplash(){
  String th=prefs.getString("theme","light");
  int c1,c2,leaf;
  if("dark".equals(th)){c1=0xFF2A4436;c2=0xFF1B2E23;leaf=R.drawable.splash_leaf_dark;}
  else if("forest".equals(th)){c1=0xFFEAF3E2;c2=0xFFD4E3CA;leaf=R.drawable.splash_leaf_forest;}
  else{c1=0xFFFBFCF8;c2=0xFFF0F2EB;leaf=R.drawable.splash_leaf_light;}
  FrameLayout f=new FrameLayout(this);
  android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
  g.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
  g.setGradientRadius(Math.round(620*getResources().getDisplayMetrics().density));
  g.setColors(new int[]{c1,c2});
  f.setBackground(g);
  f.setClickable(true);
  ImageView img=new ImageView(this);
  img.setImageResource(leaf);
  int sz=Math.round(122*getResources().getDisplayMetrics().density);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(sz,sz);
  lp.gravity=Gravity.CENTER;
  f.addView(img,lp);
  root.addView(f,new FrameLayout.LayoutParams(-1,-1));
  splash=f;
  img.setAlpha(0f);img.setScaleX(.96f);img.setScaleY(.96f);
  img.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
  root.postDelayed(()->{if(splash!=null)hideSplash();},1800);
 }
 void hideSplash(){final View s=splash;if(s==null)return;splash=null;s.animate().alpha(0f).setDuration(260).setStartDelay(120).withEndAction(()->root.removeView(s)).start();}
 static byte[] readLimited(InputStream in,int limit)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=source.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("文件超过大小限制");out.write(buffer,0,n);}return out.toByteArray();}}
 class Bridge {
  @JavascriptInterface public void setTheme(String t){prefs.edit().putString("theme",t).apply();}
  @JavascriptInterface public String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
  @JavascriptInterface public synchronized String readState(){try{return new String(readLimited(state.openRead(),25000000),StandardCharsets.UTF_8);}catch(FileNotFoundException e){return "";}catch(Exception e){return "{读取错误}";}}
  @JavascriptInterface public synchronized String saveState(String json){FileOutputStream out=null;try{if(locked)return "请先解锁应用";if(json.length()>21000000)return "数据过大，请先备份";JSONObject object=new JSONObject(json);if(object.getInt("schema")!=1)return "数据版本无效";if(state.getBaseFile().exists()){byte[] old=readLimited(state.openRead(),25000000);AtomicFile previous=new AtomicFile(new File(getFilesDir(),"ledger.previous.json"));FileOutputStream backup=previous.startWrite();try{backup.write(old);previous.finishWrite(backup);}catch(Exception e){previous.failWrite(backup);throw e;}}out=state.startWrite();out.write(json.getBytes(StandardCharsets.UTF_8));state.finishWrite(out);return "ok";}catch(Exception e){if(out!=null)state.failWrite(out);return "本地保存失败："+e.getMessage();}}
  @JavascriptInterface public void exportFile(String name,String mime,String content){if(locked)return;runOnUiThread(()->createFile(name,mime,content.getBytes(StandardCharsets.UTF_8)));}
  @JavascriptInterface public void exportXlsx(String name,String rows){if(locked)return;try{byte[] b=Spreadsheet.write(new JSONArray(rows));runOnUiThread(()->createFile(name,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",b));}catch(Exception e){toast("导出失败："+e.getMessage());}}
  @JavascriptInterface public void importFile(){if(locked)return;runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");try{startActivityForResult(i,102);}catch(Exception e){toast("无法打开文件选择器");}});}
  @JavascriptInterface public String setReminder(String time){if(!time.isEmpty()&&!time.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))return "时间格式无效";prefs.edit().putString("reminder",time).apply();runOnUiThread(()->{if(!time.isEmpty()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},10);scheduleReminder(time);});return "ok";}
  @JavascriptInterface public void showLockSettings(){runOnUiThread(()->lockSettings());}
  @JavascriptInterface public void exportPdf(String json){if(locked)return;runOnUiThread(()->{if(pdfBusy||exportBytes!=null){toast("请先完成上一次文件导出");return;}pdfBusy=true;toast("正在生成 PDF 月报");new Thread(()->{try{byte[] bytes=PdfReport.write(new JSONObject(json));runOnUiThread(()->{pdfBusy=false;if(!isFinishing()&&!isDestroyed())createFile("青禾账本-月报.pdf","application/pdf",bytes);});}catch(Exception e){runOnUiThread(()->{pdfBusy=false;toast("月报生成失败："+e.getMessage());});}}).start();});}
 }
 void createFile(String name,String mime,byte[] bytes){if(exportBytes!=null){toast("请先完成上一次文件保存");return;}exportBytes=bytes;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(i,101);}catch(Exception e){exportBytes=null;toast("无法打开文件保存位置");}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==103){if(fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);fileCallback=null;}return;}if(result!=RESULT_OK||data==null||data.getData()==null){if(request==101)exportBytes=null;return;}Uri uri=data.getData();if(request==101){byte[] bytes=exportBytes;exportBytes=null;if(bytes==null){toast("保存已中断，请重新导出");return;}new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("文件不可写");out.write(bytes);out.flush();toast("已保存到你选择的位置");}catch(Exception e){toast("保存失败："+e.getMessage());}}).start();}else if(request==102){new Thread(()->{try{String name="导入文件";try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)name=c.getString(idx);}}byte[] bytes=readLimited(getContentResolver().openInputStream(uri),25000000);String kind="text",text;if(name.toLowerCase(Locale.ROOT).endsWith(".xlsx")){text=Spreadsheet.read(bytes).toString();kind="grid";}else if(name.toLowerCase(Locale.ROOT).endsWith(".xls")){throw new IOException("旧版 XLS 请先用 Excel / WPS 转存为 XLSX");}else{try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){text=Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString();}}js("App.receiveFile("+JSONObject.quote(name)+","+JSONObject.quote(text)+","+JSONObject.quote(kind)+")");}catch(Exception e){toast("读取失败："+e.getMessage());}}).start();}}
 void scheduleReminder(String time){try{ReminderReceiver.schedule(this,time);}catch(Exception e){toast("提醒设置失败，请重新设置时间");}}
 @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==10&&(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED))toast("通知权限未开启，系统不会显示记账提醒");}
 boolean hasPin(){return prefs.contains("pin_hash");}
 String hash(String pin,String salt)throws Exception{byte[] data=Base64.decode(salt,Base64.NO_WRAP);PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),data,120000,256);try{return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(),Base64.NO_WRAP);}finally{spec.clearPassword();}}
 boolean verify(String pin){try{return MessageDigest.isEqual(hash(pin,prefs.getString("pin_salt","")).getBytes(StandardCharsets.UTF_8),prefs.getString("pin_hash","").getBytes(StandardCharsets.UTF_8));}catch(Exception e){return false;}}
 EditText pinField(String hint){EditText field=new EditText(this);field.setHint(hint);field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);field.setSingleLine(true);field.setPadding(25,20,25,20);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});return field;}
 void dismissLock(){if(lockView!=null){root.removeView(lockView);lockView=null;}if(choiceView!=null){root.removeView(choiceView);choiceView=null;}}
 void unlock(boolean cold){locked=true;if(web!=null)web.setVisibility(View.INVISIBLE);if(lockView!=null)return;LockView v=new LockView(this,"解锁青禾账本","密码只保存在本机。忘记密码无法找回，请保留外部备份。",true);int pinLen=prefs.getInt("pin_len",0);if(pinLen==4||pinLen==6)v.setExact(pinLen);lockView=v;v.setListener(new LockView.Listener(){public void onCancel(){finish();}public void onSubmit(String pin){long wait=prefs.getLong("pin_wait",0)-System.currentTimeMillis();if(wait>0){v.setMessage("请在 "+((wait+999)/1000)+" 秒后重试");v.clear();return;}v.setBusy(true);v.setMessage("验证中…");new Thread(()->{final boolean ok=verify(pin);runOnUiThread(()->{v.setBusy(false);if(ok){prefs.edit().putInt("pin_fails",0).remove("pin_wait").apply();locked=false;dismissLock();startWeb();if(ready){js("App.refreshRules()");dispatchQuick();}}else{int fails=prefs.getInt("pin_fails",0)+1;prefs.edit().putInt("pin_fails",fails).putLong("pin_wait",fails>=5?System.currentTimeMillis()+30000:0).apply();v.clear();v.setMessage(fails>=5?"输入错误，请 30 秒后再试":"密码不正确，请重试");}});}).start();}});root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
 void lockSettings(){if(!hasPin()){newPin();return;}if(lockView!=null)return;LockView v=new LockView(this,"验证当前密码","修改或关闭密码锁需要先验证",true);int pinLen=prefs.getInt("pin_len",0);if(pinLen==4||pinLen==6)v.setExact(pinLen);lockView=v;v.setListener(new LockView.Listener(){public void onCancel(){dismissLock();}public void onSubmit(String pin){v.setBusy(true);v.setMessage("验证中…");new Thread(()->{final boolean ok=verify(pin);runOnUiThread(()->{v.setBusy(false);if(!ok){v.clear();v.setMessage("密码不正确，请重试");return;}dismissLock();ChoiceView c=new ChoiceView(MainActivity.this,"应用密码",new String[]{"修改密码","关闭密码锁"},new ChoiceView.L(){public void onPick(int i){dismissLock();if(i==0)newPin();else{prefs.edit().remove("pin_hash").remove("pin_salt").remove("pin_len").apply();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);toast("密码锁已关闭");}}public void onCancel(){dismissLock();}});choiceView=c;root.addView(c,new FrameLayout.LayoutParams(-1,-1));});}).start();}});root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
 void newPin(){if(lockView!=null)return;ChoiceView c=new ChoiceView(this,"密码位数",new String[]{"4 位数字","6 位数字"},new ChoiceView.L(){public void onPick(int i){dismissLock();startNewPin(i==0?4:6);}public void onCancel(){dismissLock();}});choiceView=c;root.addView(c,new FrameLayout.LayoutParams(-1,-1));}
 void startNewPin(final int len){if(lockView!=null)return;LockView v=new LockView(this,"设置本机密码","用于保护应用入口。备份文件本身不加密，请自行妥善保管。",true);v.setExact(len);lockView=v;final String[] first={""};v.setListener(new LockView.Listener(){public void onCancel(){dismissLock();}public void onSubmit(String pin){if(first[0].isEmpty()){first[0]=pin;v.clear();v.setTitle("再输入一次");v.setMessage("确认刚才输入的密码");return;}if(!pin.equals(first[0])){first[0]="";v.clear();v.setTitle("设置本机密码");v.setMessage("两次输入不同，请重新设置");return;}v.setBusy(true);new Thread(()->{try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);String encoded=Base64.encodeToString(salt,Base64.NO_WRAP),result=hash(pin,encoded);prefs.edit().putString("pin_salt",encoded).putString("pin_hash",result).putInt("pin_len",len).putInt("pin_fails",0).remove("pin_wait").commit();runOnUiThread(()->{v.setBusy(false);dismissLock();getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);toast("本机密码已设置");});}catch(Exception e){runOnUiThread(()->{v.setBusy(false);toast("密码设置失败");dismissLock();});}}).start();}});root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
 @Override protected void onStop(){super.onStop();backgroundAt=SystemClock.elapsedRealtime();if(hasPin()&&web!=null)web.setVisibility(View.INVISIBLE);}
 @Override protected void onStart(){super.onStart();if(web!=null&&!locked){if(hasPin()&&backgroundAt>0&&SystemClock.elapsedRealtime()-backgroundAt>60000)unlock(false);else{web.setVisibility(View.VISIBLE);if(ready)js("App.refreshRules()");}}}
 @Override public void onBackPressed(){if(lockView!=null)return;if(web!=null&&ready&&!locked)web.evaluateJavascript("App.back()",value->{if(!"true".equals(value))super.onBackPressed();});else super.onBackPressed();}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}super.onDestroy();}
}
