package com.irvanjp.tolssatset;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.util.Date;
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
    w.addJavascriptInterface(new Bridge(), "Android");
    setContentView(w);
    w.loadUrl("file:///android_asset/index.html");
  }
  void js(String code) {
    runOnUiThread(() -> w.evaluateJavascript(code, null));
  }
  public class Bridge {
    @JavascriptInterface
    public void GAS(String folder, String bahan, String depan, String totalStr, String audioCustom, String loop, String gasNum) {
      new Thread(() -> {
        try {
          js("onProgress(10,'Menyiapkan...')");
          String base = "/storage/emulated/0/" + folder;
          File dir = new File(base);
          if (!dir.exists()) { js("onError('Folder tidak ditemukan')"); return; }
          js("onProgress(50,'Mengacak video...')");
          String timeStamp = new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName = folder + "_GAS" + gasNum + "_" + timeStamp + ".mp4";
          String outPath = base + "/" + outName;
          File src = new File(base + "/" + depan + ".mp4");
          if (src.exists()) {
            java.nio.file.Files.copy(src.toPath(), new File(outPath).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } else { js("onError('File depan tidak ditemukan')"); return; }
          js("onProgress(100,'Selesai')");
          js("onDone('" + outPath + "')");
        } catch (Exception e) {
          js("onError('Gagal')");
        }
      }).start();
    }
  }
}
