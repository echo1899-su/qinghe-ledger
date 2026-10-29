package local.qinghe.ledger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.xmlpull.v1.*;
import android.util.Xml;

/** Small, bounded OOXML reader/writer. It never evaluates formulas or follows external relationships. */
public final class Spreadsheet {
 static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
 static void entry(ZipOutputStream out,String path,String content)throws Exception{out.putNextEntry(new ZipEntry(path));out.write(content.getBytes(StandardCharsets.UTF_8));out.closeEntry();}
 static String col(int n){String s="";for(n++;n>0;n=(n-1)/26)s=(char)('A'+(n-1)%26)+s;return s;}
 public static byte[] write(JSONArray rows)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){
   entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
   entry(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
   entry(z,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"青禾账目\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
   entry(z,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
   StringBuilder sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"12\" width=\"18\" customWidth=\"1\"/></cols><sheetData>");
   for(int i=0;i<rows.length();i++){JSONArray row=rows.getJSONArray(i);sheet.append("<row r=\"").append(i+1).append("\">");for(int j=0;j<row.length();j++){String value=row.optString(j,"");sheet.append("<c r=\"").append(col(j)).append(i+1).append("\" ");if(i>0&&j==3&&value.matches("[0-9]+(\\.[0-9]{1,2})?"))sheet.append("t=\"n\"><v>").append(value).append("</v></c>");else sheet.append("t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}sheet.append("</row>");}
   sheet.append("</sheetData></worksheet>");entry(z,"xl/worksheets/sheet1.xml",sheet.toString());
  }return bytes.toByteArray();
 }
 static XmlPullParser parser(byte[] b)throws Exception{String text=new String(b,StandardCharsets.UTF_8);if(text.contains("<!DOCTYPE")||text.contains("<!ENTITY"))throw new IOException("不支持包含外部实体的表格");XmlPullParser p=Xml.newPullParser();p.setInput(new StringReader(text));return p;}
 public static JSONArray read(byte[] data)throws Exception{
  Map<String,byte[]> files=new HashMap<>();int size=0,count=0;try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;byte[] buffer=new byte[8192];while((e=z.getNextEntry())!=null){if(++count>2000)throw new IOException("Excel 文件结构过大");String n=e.getName();boolean keep=n.equals("xl/sharedStrings.xml")||n.matches("xl/worksheets/[^/]+\\.xml");ByteArrayOutputStream b=new ByteArrayOutputStream();int r;while((r=z.read(buffer))!=-1){size+=r;if(size>70000000)throw new IOException("Excel 解压后超过 70 MB");if(keep)b.write(buffer,0,r);}if(keep)files.put(n,b.toByteArray());}}
  List<String> shared=new ArrayList<>();if(files.containsKey("xl/sharedStrings.xml")){XmlPullParser p=parser(files.get("xl/sharedStrings.xml"));StringBuilder value=new StringBuilder();int t;while((t=p.next())!=XmlPullParser.END_DOCUMENT){if(t==XmlPullParser.START_TAG&&p.getName().equals("si"))value.setLength(0);if(t==XmlPullParser.START_TAG&&p.getName().equals("t"))value.append(p.nextText());if(t==XmlPullParser.END_TAG&&p.getName().equals("si")){shared.add(value.toString());if(shared.size()>500000)throw new IOException("表格字符串过多");}}}
  JSONArray best=null;int score=-1;List<String> names=new ArrayList<>(files.keySet());Collections.sort(names);
  for(String n:names){if(!n.startsWith("xl/worksheets/"))continue;JSONArray rows=new JSONArray();XmlPullParser p=parser(files.get(n));JSONArray row=null;int column=0;String type="",value="";boolean formula=false;int t;while((t=p.next())!=XmlPullParser.END_DOCUMENT){String tag=p.getName();if(t==XmlPullParser.START_TAG&&"row".equals(tag))row=new JSONArray();else if(t==XmlPullParser.START_TAG&&"c".equals(tag)){String ref=p.getAttributeValue(null,"r");column=0;if(ref!=null)for(char c:ref.toCharArray()){if(c<'A'||c>'Z')break;column=column*26+(c-'A'+1);}column=Math.max(0,column-1);if(column>511)throw new IOException("表格列数超过 512");type=p.getAttributeValue(null,"t");value="";formula=false;}else if(t==XmlPullParser.START_TAG&&"f".equals(tag)){formula=true;p.nextText();}else if(t==XmlPullParser.START_TAG&&("v".equals(tag)||"t".equals(tag)))value+=p.nextText();else if(t==XmlPullParser.END_TAG&&"c".equals(tag)&&row!=null){if("s".equals(type)&&!value.isEmpty()){int index=Integer.parseInt(value);value=index>=0&&index<shared.size()?shared.get(index):"";}if("b".equals(type))value=value.equals("1")?"TRUE":"FALSE";if(formula&&value.isEmpty())value="";while(row.length()<column)row.put("");row.put(value);}else if(t==XmlPullParser.END_TAG&&"row".equals(tag)&&row!=null){rows.put(row);if(rows.length()>100000)throw new IOException("表格超过 100000 行，请分批导出");}}
   int candidate=rows.length();for(int i=0;i<Math.min(30,rows.length());i++){String txt=rows.getJSONArray(i).toString();if((txt.contains("金额")||txt.contains("amount"))&&(txt.contains("日期")||txt.contains("时间")||txt.contains("date"))){candidate+=1000000;break;}}if(candidate>score){score=candidate;best=rows;}
  }if(best==null)throw new IOException("未找到可读取的工作表，请转存为标准 XLSX 或 CSV");return best;
 }
}
