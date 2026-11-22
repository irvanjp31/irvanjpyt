package com.irvanjp.tolssatset;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaFormat;
import android.media.MediaCodec;
import java.io.File;
import java.util.*;
import java.text.SimpleDateFormat;
import java.nio.ByteBuffer;
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
  void js(String code) { runOnUiThread(() -> w.evaluateJavascript(code, null)); }
  public class Bridge {
    @JavascriptInterface
    public void GAS(String folder, String bahan, String depan, String totalStr, String audioCustom, String loop, String gasNum) {
      new Thread(() -> {
        try {
          int total = Integer.parseInt(totalStr);
          long limitUs = (long) total * 1000000L;
          String base = "/storage/emulated/0/" + folder;
          js("onProgress(5,'Menyiapkan...')");
          List<String> bahanList = new ArrayList<>();
          for (String p : bahan.split("[\\s,]+")) if (!p.isEmpty()) bahanList.add(p.trim());
          if (bahanList.isEmpty()) { js("onError('Bahan kosong')"); return; }
          List<String> order = new ArrayList<>();
          order.add(depan);
          List<String> shuffled = new ArrayList<>(bahanList);
          Collections.shuffle(shuffled);
          for (int i = 0; i < 2000; i++) for (String s : shuffled) order.add(s);
          js("onProgress(20,'Mengacak video...')");
          String timeStamp = new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName = folder + "_GAS" + gasNum + "_" + timeStamp + ".mp4";
          String outPath = base + "/" + outName;
          File outFile = new File(outPath);
          if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
          MediaMuxer muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
          int videoTrack = -1;
          int audioTrack = -1;
          long videoPts = 0;
          long audioPts = 0;
          ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
          boolean muxerStarted = false;
          String firstVideo = base + "/" + depan + ".mp4";
          MediaExtractor ex0 = new MediaExtractor();
          ex0.setDataSource(firstVideo);
          MediaFormat vfmt = null;
          for (int i = 0; i < ex0.getTrackCount(); i++) {
            MediaFormat fm = ex0.getTrackFormat(i);
            String mime = fm.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) { vfmt = fm; break; }
          }
          if (vfmt == null) { js("onError('Video depan tidak ada track video')"); return; }
          videoTrack = muxer.addTrack(vfmt);
          ex0.release();
          String audClean = audioCustom.replaceAll("(?i)lalu", " ").replaceAll(",", " ").trim();
          String[] audParts = audClean.split("\\s+");
          String firstAudioName = audParts.length > 0 ? audParts[0] : "g";
          File audioFile = new File(base + "/" + firstAudioName + ".mp3");
          if (!audioFile.exists()) { js("onError('Audio MP3 tidak ditemukan')"); return; }
          MediaFormat afmt = null;
          String audioPath = audioFile.getAbsolutePath();
          MediaExtractor aex0 = new MediaExtractor();
          aex0.setDataSource(audioPath);
          for (int i = 0; i < aex0.getTrackCount(); i++) {
            MediaFormat fm = aex0.getTrackFormat(i);
            String mime = fm.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { afmt = fm; break; }
          }
          aex0.release();
          if (afmt != null) {
            try {
              String mime = afmt.getString(MediaFormat.KEY_MIME);
              if (mime != null && mime.equals("audio/mpeg")) {
                MediaFormat aacFmt = MediaFormat.createAudioFormat("audio/mp4a-latm", afmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), afmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                aacFmt.setInteger(MediaFormat.KEY_AAC_PROFILE, 2);
                aacFmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
                audioTrack = muxer.addTrack(aacFmt);
              } else {
                audioTrack = muxer.addTrack(afmt);
              }
            } catch (Exception e) { audioTrack = -1; }
          }
          muxer.start();
          muxerStarted = true;
          js("onProgress(40,'Menggabungkan video...')");
          for (String v : order) {
            if (videoPts >= limitUs) break;
            File f = new File(base + "/" + v + ".mp4");
            if (!f.exists()) continue;
            MediaExtractor ex = new MediaExtractor();
            try { ex.setDataSource(f.getAbsolutePath()); } catch (Exception e) { ex.release(); continue; }
            int track = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
              MediaFormat fm = ex.getTrackFormat(i);
              String mime = fm.getString(MediaFormat.KEY_MIME);
              if (mime != null && mime.startsWith("video/")) { track = i; break; }
            }
            if (track < 0) { ex.release(); continue; }
            ex.selectTrack(track);
            long lastPts = 0;
            while (true) {
              int sz = ex.readSampleData(buf, 0);
              if (sz < 0) break;
              long pts = ex.getSampleTime();
              if (pts < 0) break;
              if (videoPts + pts >= limitUs) break;
              MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
              info.offset = 0; info.size = sz; info.presentationTimeUs = videoPts + pts; info.flags = ex.getSampleFlags();
              muxer.writeSampleData(videoTrack, buf, info);
              lastPts = pts;
              ex.advance();
            }
            videoPts += lastPts + 33333;
            ex.release();
            int p = 40 + (int)(videoPts * 40 / limitUs);
            if (p > 80) p = 80;
            js("onProgress(" + p + ",'Menggabungkan video...')");
          }
          if (audioTrack >= 0 && audioPath != null) {
            js("onProgress(85,'Menggabungkan audio...')");
            List<String> audioList = new ArrayList<>();
            for (String a : audParts) if (!a.isEmpty()) audioList.add(a);
            if (audioList.isEmpty()) audioList.add(firstAudioName);
            int repeat = "y".equals(loop) ? 50 : 1;
            for (int r = 0; r < repeat; r++) {
              for (String aName : audioList) {
                if (audioPts >= limitUs) break;
                File af = new File(base + "/" + aName + ".mp3");
                if (!af.exists()) continue;
                MediaExtractor aex = new MediaExtractor();
                try { aex.setDataSource(af.getAbsolutePath()); } catch (Exception e) { aex.release(); continue; }
                int atrack = -1;
                for (int i = 0; i < aex.getTrackCount(); i++) {
                  MediaFormat fm = aex.getTrackFormat(i);
                  String mime = fm.getString(MediaFormat.KEY_MIME);
                  if (mime != null && mime.startsWith("audio/")) { atrack = i; break; }
                }
                if (atrack < 0) { aex.release(); continue; }
                aex.selectTrack(atrack);
                long lastA = 0;
                while (true) {
                  int sz = aex.readSampleData(buf, 0);
                  if (sz < 0) break;
                  long pts = aex.getSampleTime();
                  if (pts < 0) break;
                  if (audioPts + pts >= limitUs) break;
                  MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                  info.offset = 0; info.size = sz; info.presentationTimeUs = audioPts + pts; info.flags = aex.getSampleFlags();
                  muxer.writeSampleData(audioTrack, buf, info);
                  lastA = pts;
                  aex.advance();
                }
                audioPts += lastA + 20000;
                aex.release();
              }
              if (audioPts >= limitUs) break;
            }
          }
          if (muxerStarted) { try { muxer.stop(); } catch (Exception e) {} muxer.release(); }
          js("onProgress(100,'Selesai')");
          js("onDone('" + outPath + "')");
        } catch (Exception e) {
          String msg = e.getMessage() != null ? e.getMessage().replace("'", "") : "Gagal";
          js("onError('" + msg + "')");
        }
      }).start();
    }
  }
        }
