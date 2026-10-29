package local.qinghe.ledger;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
public class QuickWidget extends AppWidgetProvider {
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
  v.setOnClickPendingIntent(R.id.widget_title,PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
  v.setOnClickPendingIntent(R.id.widget_expense,PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class).putExtra("quick","expense"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
  v.setOnClickPendingIntent(R.id.widget_income,PendingIntent.getActivity(c,2,new Intent(c,MainActivity.class).putExtra("quick","income"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);
 }}
}
