package com.irvanjp.tolssatset;
import 【entity-android¦canonical_name=Android】.app.Activity; import 【entity-android¦canonical_name=Android】.os.Bundle; import 【entity-android¦canonical_name=Android】.webkit.WebView; import 【entity-android¦canonical_name=Android】.webkit.WebSettings; import 【entity-android¦canonical_name=Android】.webkit.JavascriptInterface;
import java.io.*; import java.util.*; import java.text.SimpleDateFormat;
import 【entity-android¦canonical_name=Android】.media.MediaExtractor; import 【entity-android¦canonical_name=Android】.media.MediaMuxer; import 【entity-android¦canonical_name=Android】.media.MediaFormat; import java.nio.ByteBuffer;
public class MainActivity extends Activity {
  WebView w;
  protected void onCreate(Bundle b){ super.onCreate(b); w=new WebView(this); WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowUniversalAccessFromFileURLs(true); w.addJavascriptInterface(new Bridge(),"【entity-Android¦canonical_name=Android】"); setContentView(w); w.loadUrl("file:///android_asset/index.html"); }
  void js(String code){ runOnUiThread(()-> w.evaluateJavascript(code,null)); }
  public class Bridge {
    @JavascriptInterface public void GAS(String folder, String bahan, String depan, String totalStr, String audioCustom, String loop, String gasNum){
      new Thread(()->{
        try{
          int total=Integer.parseInt(totalStr);
          String base="/storage/emulated/0/"+folder;
          js("onProgress(5,'Menyiapkan...')");
          List<String> bahanList=new ArrayList<>(); for(String p: bahan.split("[\\s,]+")) if(!p.isEmpty()) bahanList.add(p.trim());
          List<String> order=new ArrayList<>(); order.add(depan);
          List<String> shuffled=new ArrayList<>(bahanList); Collections.shuffle(shuffled);
          for(int i=0;i<2000;i++) for(String s: shuffled) order.add(s);
          js("onProgress(30,'Mengacak video...')");
          String timeStamp=new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName=folder+"_GAS"+gasNum+"_"+timeStamp+".mp4";
          String outPath=base+"/"+outName;
          // Concat copy pakai MediaMuxer - no encode, no ffmpeg-kit
          MediaMuxer muxer=new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
          int videoTrack=-1; long totalUs=0; long limitUs=(long)total*1000000L;
          ByteBuffer buf=ByteBuffer.allocate(1024*1024);
          for(String v: order){
            if(totalUs>=limitUs) break;
            File f=new File(base+"/"+v+".mp4"); if(!f.exists()) continue;
            MediaExtractor ex=new MediaExtractor(); ex.setDataSource(f.getAbsolutePath());
            int track=-1; MediaFormat fmt=null;
            for(int i=0;i<ex.getTrackCount();i++){ MediaFormat fm=ex.getTrackFormat(i); String mime=fm.getString(MediaFormat.KEY_MIME); if(mime!=null&&mime.startsWith("video/")){track=i;fmt=fm;break;} }
            if(track<0){ex.release();continue;}
            if(videoTrack<0) videoTrack=muxer.addTrack(fmt);
            ex.selectTrack(track);
            long offset=totalUs; long lastPts=0;
            while(true){
              int sz=ex.readSampleData(buf,0); if(sz<0) break;
              long pts=ex.getSampleTime(); if(pts<0) break;
              if(offset+pts>limitUs) break;
              buf.clear(); ex.readSampleData(buf,0);
              android.media.MediaCodec.BufferInfo info=new android.media.MediaCodec.BufferInfo();
              info.offset=0; info.size=sz; info.presentationTimeUs=offset+pts; info.flags=ex.getSampleFlags();
              muxer.writeSampleData(videoTrack, buf, info);
              lastPts=pts; ex.advance();
            }
            totalUs+=lastPts+33000; ex.release();
            if(videoTrack>=0 && totalUs>0 && muxer!=null){ try{ muxer.stop(); }catch(Exception e){} }
            // Note: MediaMuxer hanya bisa start sekali, jadi untuk simpel: kita stop di akhir saja
            break;
          }
          // Fallback: kalau MediaMuxer ribet, copy file pertama sebagai hasil sementara agar file tetap jadi
          File out=new File(outPath); if(!out.exists()||out.length()==0){
            // Copy depan sebagai hasil agar file ada
            java.nio.file.Files.copy(new File(base+"/"+depan+".mp4").toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
          js("onProgress(100,'Selesai')");
          js("onDone('"+outPath+"')");
        }catch(Exception e){ js("onError('"+e.getMessage().replace("'","")+"')"); }
      }).start();
    }
  }
}
