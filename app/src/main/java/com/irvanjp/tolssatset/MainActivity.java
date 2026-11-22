
package com.irvanjp.tolssatset;
import android.app.Activity; import android.os.Bundle; import android.webkit.WebView; import android.webkit.WebSettings; import android.webkit.JavascriptInterface; import android.widget.Toast;
import java.io.*; import java.util.*; import java.text.SimpleDateFormat;
import com.arthenica.ffmpegkit.FFmpegKit; import com.arthenica.ffmpegkit.ReturnCode;
public class MainActivity extends Activity {
  WebView w;
  protected void onCreate(Bundle b){ super.onCreate(b); w=new WebView(this); WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowUniversalAccessFromFileURLs(true); w.addJavascriptInterface(new Bridge(),"Android"); setContentView(w); w.loadUrl("file:///android_asset/index.html"); }
  void js(String code){ runOnUiThread(()-> w.evaluateJavascript(code,null)); }
  public class Bridge {
    @JavascriptInterface public void GAS(String folder, String bahan, String depan, String totalStr, String audioCustom, String loop, String gasNum){
      new Thread(()->{
        try{
          int total=Integer.parseInt(totalStr);
          String base="/storage/emulated/0/"+folder;
          File dir=new File(base); if(!dir.exists()){ js("onError('Folder "+folder+" tidak ditemukan')"); return; }
          js("onProgress(5,'Menyiapkan...')");
          // Parse bahan: split by space/comma
          List<String> bahanList=new ArrayList<>(); for(String p: bahan.split("[\\s,]+")) if(!p.isEmpty()) bahanList.add(p.trim());
          if(bahanList.isEmpty()) bahanList.add("a");
          // Check files
          for(String p: bahanList){ if(!new File(base+"/"+p+".mp4").exists()){ js("onError('File "+p+".mp4 tidak ada di "+folder+"')"); return; } }
          // Build list: depan first, then shuffled
          List<String> shuffled=new ArrayList<>(bahanList); Collections.shuffle(shuffled);
          // Ensure depan is first
          List<String> order=new ArrayList<>(); order.add(depan);
          // Fill until we have enough entries: repeat shuffled many times to reach total duration approx (we use -t to cut, so need long list)
          for(int i=0;i<2000;i++){ for(String s: shuffled) order.add(s); }
          File listFile=new File(getCacheDir(),"list.txt");
          BufferedWriter bw=new BufferedWriter(new FileWriter(listFile));
          for(String v: order) bw.write("file '"+base+"/"+v+".mp4'\n");
          bw.close();
          js("onProgress(30,'Mengacak video...')");
          String timeStamp=new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName=folder+"_GAS"+gasNum+"_"+timeStamp+".mp4";
          String outPath=base+"/"+outName;
          String tmpPath=getCacheDir()+"/tmp.mp4";
          // Step 1: concat video no encode, mute audio, cut to target
          String cmd1="-y -f concat -safe 0 -i "+listFile.getAbsolutePath()+" -t "+total+" -an -c copy "+tmpPath;
          com.arthenica.ffmpegkit.Session s1=FFmpegKit.execute(cmd1);
          if(!ReturnCode.isSuccess(s1.getReturnCode())){ js("onError('Gagal gabung video')"); return; }
          js("onProgress(60,'Menggabungkan audio...')");
          // Parse audio: split by space/comma/lalu
          String audClean=audioCustom.replaceAll("(?i)lalu"," ").replaceAll(","," ").trim();
          String[] audParts=audClean.split("\\s+");
          String firstAudio=audParts.length>0?audParts[0]:"g";
          File audioFile=new File(base+"/"+firstAudio+".mp3"); if(!audioFile.exists()) audioFile=new File(base+"/"+firstAudio+".m4a");
          if(!audioFile.exists()){ js("onError('Audio "+firstAudio+" tidak ditemukan')"); return; }
          // If loop = y, build looped audio via concat copy repeat: create audio list
          String audioInput=audioFile.getAbsolutePath();
          if("y".equals(loop)){
            File aList=new File(getCacheDir(),"alist.txt"); BufferedWriter aw=new BufferedWriter(new FileWriter(aList));
            for(int i=0;i<20;i++) aw.write("file '"+audioInput+"'\n"); aw.close();
            String loopAudio=getCacheDir()+"/loop.mp3";
            FFmpegKit.execute("-y -f concat -safe 0 -i "+aList.getAbsolutePath()+" -c copy "+loopAudio);
            audioInput=loopAudio;
          }
          String cmd2="-y -i "+tmpPath+" -i "+audioInput+" -map 0:v:0 -map 1:a:0 -c copy -t "+total+" -movflags +faststart "+outPath;
          com.arthenica.ffmpegkit.Session s2=FFmpegKit.execute(cmd2);
          if(!ReturnCode.isSuccess(s2.getReturnCode())){ js("onError('Gagal gabung audio')"); return; }
          js("onProgress(100,'Selesai')");
          js("onDone('"+outPath+"')");
        }catch(Exception e){ js("onError('"+e.getMessage().replace("'","")+"')"); }
      }).start();
    }
  }
              }
