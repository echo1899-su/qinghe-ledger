package local.qinghe.ledger;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import org.json.*;
import java.io.*;

/** A locally generated, paginated report independent of installed printers. */
public final class PdfReport {
 final PdfDocument document=new PdfDocument();final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 PdfDocument.Page page;Canvas canvas;float y;int pages;String heading;
 final int green=Color.rgb(35,85,64),ink=Color.rgb(35,45,39),muted=Color.rgb(100,112,105);
 public static byte[] write(JSONObject data)throws Exception{
  PdfReport report=new PdfReport();
  try{report.render(data);report.end();ByteArrayOutputStream out=new ByteArrayOutputStream();report.document.writeTo(out);
   if(out.size()>25000000)throw new IOException("月报过大，请缩小范围或改用表格导出");return out.toByteArray();
  }finally{if(report.page!=null)report.document.finishPage(report.page);report.document.close();}
 }
 void newPage()throws IOException{
  end();if(++pages>200)throw new IOException("月报超过 200 页，请缩小范围或改用表格导出");
  page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,pages).create());canvas=page.getCanvas();
  canvas.drawColor(Color.WHITE);paint.setColor(green);paint.setTextSize(18);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
  canvas.drawText("青禾账本 · 收支月报",40,50,paint);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
  paint.setTextSize(10);paint.setColor(muted);canvas.drawText(heading,40,72,paint);y=98;
 }
 void end(){if(page==null)return;paint.setColor(muted);paint.setTextSize(9);canvas.drawText("本机生成 · 转账不计入收支 · "+pages+" 页",40,814,paint);document.finishPage(page);page=null;}
 void room(float height)throws IOException{if(page==null||y+height>786)newPage();}
 void line(String value,float size,int color)throws IOException{
  String[] paragraphs=value.replace('\r',' ').split("\n",-1);
  for(String paragraph:paragraphs){String rest=paragraph;if(rest.isEmpty()){room(size+6);y+=size+6;continue;}
   while(!rest.isEmpty()){room(size+6);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(size);paint.setColor(color);
    int count=paint.breakText(rest,true,515,null);if(count<1)count=1;
    if(count<rest.length()&&Character.isHighSurrogate(rest.charAt(count-1)))count--;
    if(count<1)count=Math.min(2,rest.length());
    canvas.drawText(rest.substring(0,count),40,y,paint);y+=size+6;rest=rest.substring(count);
   }
  }
 }
 void section(String title)throws IOException{room(44);y+=14;line(title,14,green);}
 void render(JSONObject data)throws Exception{
  JSONArray rows=data.getJSONArray("transactions");if(rows.length()>5000)throw new IOException("月报超过 5000 笔，请改用表格导出");
  heading=data.getString("month")+" · "+data.getString("book");newPage();
  line("共 "+rows.length()+" 笔记录",11,muted);
  line("收入  ¥"+data.getString("income")+"     支出  ¥"+data.getString("expense"),14,ink);
  line("本月结余  ¥"+data.getString("net"),16,green);
  section("支出分类");JSONArray groups=data.getJSONArray("categories");
  if(groups.length()==0)line("本月暂无支出",11,muted);
  for(int i=0;i<groups.length();i++){JSONArray row=groups.getJSONArray(i);line(row.getString(0)+"    ¥"+row.getString(1)+"    "+row.getString(2),11,ink);}
  section("收支明细");
  if(rows.length()==0)line("本月暂无账目",11,muted);
  for(int i=0;i<rows.length();i++){JSONArray row=rows.getJSONArray(i);room(52);y+=5;
   line(row.getString(0)+"    "+row.getString(1)+"    "+row.getString(2),11,ink);
   line("账户："+row.getString(3),10,muted);
   if(!row.getString(4).isEmpty())line("备注："+row.getString(4),10,muted);
   room(9);paint.setColor(Color.rgb(221,230,223));canvas.drawLine(40,y,555,y,paint);y+=9;
  }
 }
}
