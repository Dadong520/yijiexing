import java.io.*;import java.nio.charset.StandardCharsets;import java.lang.reflect.*;import java.security.*;import java.util.*;import android.content.Context;import com.amap.api.maps.MapsInitializer;import com.amap.api.services.core.ServiceSettings;import com.amap.api.services.poisearch.PoiSearch;
public class SdkProbe {
 static String hex(byte[] b){return HexFormat.of().formatHex(b);}
 public static void main(String[] args)throws Exception {
  PrintStream trusted=System.out;System.setOut(new PrintStream(OutputStream.nullOutputStream()));System.setErr(new PrintStream(OutputStream.nullOutputStream()));
  try {
   BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));byte[] cert=Base64.getDecoder().decode(in.readLine());String key=in.readLine();Context ctx=new Context(args[0],cert);
   Class<?> app=Class.forName("com.amap.api.col.3sl.l9");Class<?> client=Class.forName("com.amap.api.col.3sl.o9");
   MapsInitializer.setApiKey(key);String a=(String)app.getMethod("d",Context.class).invoke(null,ctx);boolean mapKeyMatches=key.equals(a);
   ServiceSettings.getInstance().setApiKey(key);String b=(String)app.getMethod("d",Context.class).invoke(null,ctx);boolean searchKeyMatches=key.equals(b);
   String identity=(String)app.getMethod("g",Context.class).invoke(null,ctx);
   String expected=hex(MessageDigest.getInstance("SHA-1").digest(cert)).toUpperCase().replaceAll("(..)(?!$)","$1:")+":"+args[0];
   String ts=(String)client.getMethod("a").invoke(null);String params="key="+key+"&output=json";
   String scode=(String)client.getMethod("a",Context.class,String.class,String.class).invoke(null,ctx,ts,params);
   String expectedScode=hex(MessageDigest.getInstance("MD5").digest((expected+":"+ts.substring(0,ts.length()-3)+":"+params).getBytes(StandardCharsets.UTF_8)));
   PoiSearch.Query query=new PoiSearch.Query("宜昌东站","","宜昌市");query.setPageSize(1);query.setPageNum(0);
   Class<?> queryClass=Class.forName("com.amap.api.col.3sl.r7");Object qi=queryClass.getConstructor(PoiSearch.Query.class,PoiSearch.SearchBound.class).newInstance(query,null);
   Class<?> handlerClass=Class.forName("com.amap.api.col.3sl.n7");Object handler=handlerClass.getConstructor(Context.class,queryClass).newInstance(ctx,qi);
   byte[] entity=(byte[])handlerClass.getMethod("getEntityBytes").invoke(handler);
   trusted.println("{\"mapKeyMatches\":"+mapKeyMatches+",\"searchKeyMatches\":"+searchKeyMatches+",\"identityMatches\":"+expected.equals(identity)+",\"scodeMatches\":"+expectedScode.equals(scode)+",\"entityPresent\":"+(entity!=null&&entity.length>0)+"}");
   trusted.println(entity==null?"":Base64.getEncoder().encodeToString(entity));
  }catch(Throwable e){while(e instanceof InvocationTargetException)e=((InvocationTargetException)e).getTargetException();trusted.println("{\"errorType\":\""+e.getClass().getName()+"\"}");}
 }
}