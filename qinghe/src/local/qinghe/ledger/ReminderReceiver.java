package local.qinghe.ledger;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.content.pm.PackageManager;
import java.util.Calendar;
public class ReminderReceiver extends BroadcastReceiver {
 public static void schedule(Context context,String time){
  AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
  PendingIntent pi=PendingIntent.getBroadcast(context,3,new Intent(context,ReminderReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  am.cancel(pi);
  if(time.isEmpty())return;
  if(!time.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new IllegalArgumentException("时间格式无效");
  String[] parts=time.split(":");Calendar c=Calendar.getInstance();
  c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));c.set(Calendar.MINUTE,Integer.parseInt(parts[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
  if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(Calendar.DATE,1);
  // A daily repeating alarm may receive an 18-hour batching window. Use a
  // bounded window and calculate the next local day after each delivery.
  am.setWindow(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),10*60*1000L,pi);
 }
 public void onReceive(Context context,Intent intent){
  String time=context.getSharedPreferences("local_settings",Context.MODE_PRIVATE).getString("reminder","");
  if(time.isEmpty())return;
  schedule(context,time);
  if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
  NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("daily","每日记账提醒",NotificationManager.IMPORTANCE_DEFAULT));
  Intent launch=new Intent(context,MainActivity.class).putExtra("quick","expense");
  PendingIntent pi=PendingIntent.getActivity(context,20,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"daily"):new Notification.Builder(context);
  b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("今天，也给生活记一笔").setContentText("打开青禾账本，记录今天的收入与支出。").setContentIntent(pi).setAutoCancel(true);
  nm.notify(1,b.build());
 }
}
