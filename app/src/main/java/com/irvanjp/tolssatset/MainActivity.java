package com.irvanjp.tolssatset;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
public class MainActivity extends Activity {
  WebView w;
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    w = new WebView(this);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowUniversalAccessFromFileURLs(true);
    w.addJavascriptInterface(new Bridge(), "Android");
    setContentView(w);
    w.loadUrl("file:///android_asset/index.html");
  }
  void js(String c) { runOnUiThread(() -> w.evaluateJavascript(c, null)); }
  String ffmpegPath() {
    try {
      File f = new File(getFilesDir(), "ffmpeg");
      if (!f.exists()) {
        InputStream in = getAssets().open("ffmpeg");
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close(); in.close();
        f.setExecutable(true);
      }
      return f.getAbsolutePath();
    } catch (Exception e) { return "ffmpeg"; }
  }
  public class Bridge {
    @JavascriptInterface
    public void GAS(String folder, String bahan, String depan, String totalStr, String audioCustom, String loop, String gasNum) {
      new Thread(() -> {
        try {
          int total = Integer.parseInt(totalStr);
          String base = "/storage/emulated/0/" + folder;
          js("onProgress(5,'Menyiapkan...')");
          List<String> bahanList = new ArrayList<>();
          for (String p : bahan.split("[\\s,]+")) if (!p.isEmpty()) bahanList.add(p.trim());
          List<String> order = new ArrayList<>();
          order.add(depan);
          List<String> sh = new ArrayList<>(bahanList);
          Collections.shuffle(sh);
          for (int i = 0; i < 2000; i++) for (String s : sh) order.add(s);
          String timeStamp = new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName = folder + "_GAS" + gasNum + "_" + timeStamp + ".mp4";
          String outPath = base + "/" + outName;
          new File(base).mkdirs();
          String listPath = base + "/list.txt";
          BufferedWriter bw = new BufferedWriter(new FileWriter(listPath));
          for (String v : order) {
            File f = new File(base + "/" + v + ".mp4");
            if (f.exists()) bw.write("file '" + f.getAbsolutePath() + "'\n");
          }
          bw.close();
          String audClean = audioCustom.replaceAll("(?i)lalu", " ").replaceAll(",", " ").trim();
          String[] audParts = audClean.split("\\s+");
          StringBuilder concatCmd = new StringBuilder();
          for (String a : audParts) {
            File af = new File(base + "/" + a + ".mp3");
            if (af.exists()) concatCmd.append("file '").append(af.getAbsolutePath()).append("'\n");
          }
          String audioListPath = base + "/audiolist.txt";
          BufferedWriter abw = new BufferedWriter(new FileWriter(audioListPath));
          abw.write(concatCmd.toString());
          abw.close();
          String concatAudio = base + "/audio.mp3";
          String ff = ffmpegPath();
          js("onProgress(40,'Gabung MP3...')");
          Process p1 = Runtime.getRuntime().exec(new String[]{"sh", "-c", ff + " -y -f concat -safe 0 -i " + audioListPath + " -c copy " + concatAudio});
          p1.waitFor();
          js("onProgress(60,'Gabung Video + MP3 No Encode...')");
          String cmd = ff + " -y -f concat -safe 0 -i " + listPath + " -i " + concatAudio + " -map 0:v:0 -map 1:a:0 -c copy -t " + total + " " + outPath;
          Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
          BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
          String line;
          while ((line = br.readLine()) != null) {}
          p.waitFor();
          js("onProgress(100,'Selesai')");
          js("onDone('" + outPath + "')");
        } catch (Exception e) {
          js("onError('" + (e.getMessage() != null ? e.getMessage().replace("'", "") : "Gagal") + "')");
        }
      }).start();
    }
  }
            }
