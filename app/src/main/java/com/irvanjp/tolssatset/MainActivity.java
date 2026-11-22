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
  void js(String c) { runOnUiThread(() -> w.evaluateJavascript(c, null)); }
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
          List<String> order = new ArrayList<>();
          order.add(depan);
          List<String> sh = new ArrayList<>(bahanList);
          Collections.shuffle(sh);
          for (int i = 0; i < 2000; i++) for (String s : sh) order.add(s);
          String timeStamp = new SimpleDateFormat("HH-mm-ss").format(new Date());
          String outName = folder + "_GAS" + gasNum + "_" + timeStamp + ".mp4";
          String outPath = base + "/" + outName;
          new File(base).mkdirs();
          MediaMuxer muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
          int videoTrack = -1;
          int audioTrack = -1;
          long videoPts = 0;
          ByteBuffer buf = ByteBuffer.allocate(1024*1024);
          String firstVideo = base + "/" + depan + ".mp4";
          MediaExtractor ex0 = new MediaExtractor();
          ex0.setDataSource(firstVideo);
          MediaFormat vfmt = null;
          for (int i = 0; i < ex0.getTrackCount(); i++) {
            MediaFormat fm = ex0.getTrackFormat(i);
            String m = fm.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("video/")) { vfmt = fm; break; }
          }
          videoTrack = muxer.addTrack(vfmt);
          ex0.release();
          String audClean = audioCustom.replaceAll("(?i)lalu", " ").replaceAll(",", " ").trim();
          String[] audParts = audClean.split("\\s+");
          String firstAudioName = audParts.length > 0 ? audParts[0] : "g";
          File audioFile = new File(base + "/" + firstAudioName + ".mp3");
          if (!audioFile.exists()) { js("onError('Audio MP3 tidak ditemukan')"); return; }
          MediaExtractor aex0 = new MediaExtractor();
          aex0.setDataSource(audioFile.getAbsolutePath());
          MediaFormat afmt = null;
          for (int i = 0; i < aex0.getTrackCount(); i++) {
            MediaFormat fm = aex0.getTrackFormat(i);
            String m = fm.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) { afmt = fm; break; }
          }
          aex0.release();
          int sampleRate = afmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          int ch = afmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          MediaFormat aacFmt = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, ch);
          aacFmt.setInteger(MediaFormat.KEY_AAC_PROFILE, 2);
          aacFmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
          audioTrack = muxer.addTrack(aacFmt);
          muxer.start();
          js("onProgress(40,'Video...')");
          for (String v : order) {
            if (videoPts >= limitUs) break;
            File f = new File(base + "/" + v + ".mp4");
            if (!f.exists()) continue;
            MediaExtractor ex = new MediaExtractor();
            try { ex.setDataSource(f.getAbsolutePath()); } catch (Exception e) { ex.release(); continue; }
            int track = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
              MediaFormat fm = ex.getTrackFormat(i);
              String m = fm.getString(MediaFormat.KEY_MIME);
              if (m != null && m.startsWith("video/")) { track = i; break; }
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
          }
          js("onProgress(85,'Audio MP3...')");
          List<String> audioList = new ArrayList<>();
          for (String a : audParts) if (!a.isEmpty()) audioList.add(a);
          if (audioList.isEmpty()) audioList.add(firstAudioName);
          long audioPts = 0;
          for (String aName : audioList) {
            File af = new File(base + "/" + aName + ".mp3");
            if (!af.exists()) continue;
            MediaExtractor decEx = new MediaExtractor();
            decEx.setDataSource(af.getAbsolutePath());
            int aTrack = -1;
            for (int i = 0; i < decEx.getTrackCount(); i++) {
              MediaFormat fm = decEx.getTrackFormat(i);
              String m = fm.getString(MediaFormat.KEY_MIME);
              if (m != null && m.startsWith("audio/")) { aTrack = i; break; }
            }
            decEx.selectTrack(aTrack);
            MediaFormat decFmt = decEx.getTrackFormat(aTrack);
            String decMime = decFmt.getString(MediaFormat.KEY_MIME);
            MediaCodec decoder = MediaCodec.createDecoderByType(decMime);
            decoder.configure(decFmt, null, null, 0);
            decoder.start();
            MediaFormat encFmt = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, ch);
            encFmt.setInteger(MediaFormat.KEY_AAC_PROFILE, 2);
            encFmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            encFmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            MediaCodec encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            ByteBuffer[] decIn = decoder.getInputBuffers();
            ByteBuffer[] decOut = decoder.getOutputBuffers();
            ByteBuffer[] encIn = encoder.getInputBuffers();
            ByteBuffer[] encOut = encoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean decDone = false, encDone = false;
            while (!encDone) {
              if (!decDone) {
                int idx = decoder.dequeueInputBuffer(10000);
                if (idx >= 0) {
                  int sz = decEx.readSampleData(decIn[idx], 0);
                  if (sz < 0) { decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); decDone = true; }
                  else { decoder.queueInputBuffer(idx, 0, sz, decEx.getSampleTime(), 0); decEx.advance(); }
                }
              }
              int outIdx = decoder.dequeueOutputBuffer(info, 10000);
              if (outIdx >= 0) {
                ByteBuffer pcm = decOut[outIdx];
                int encIdx = encoder.dequeueInputBuffer(10000);
                if (encIdx >= 0 && pcm != null && info.size > 0) {
                  ByteBuffer eb = encIn[encIdx]; eb.clear(); eb.put(pcm);
                  encoder.queueInputBuffer(encIdx, 0, info.size, info.presentationTimeUs, 0);
                }
                decoder.releaseOutputBuffer(outIdx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                  int e2 = encoder.dequeueInputBuffer(10000);
                  if (e2 >= 0) encoder.queueInputBuffer(e2, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
              }
              int eOut = encoder.dequeueOutputBuffer(info, 10000);
              if (eOut >= 0) {
                if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                  if (audioPts + info.presentationTimeUs < limitUs) {
                    MediaCodec.BufferInfo mi = new MediaCodec.BufferInfo();
                    mi.offset = 0; mi.size = info.size;
                    mi.presentationTimeUs = audioPts + info.presentationTimeUs;
                    mi.flags = info.flags;
                    muxer.writeSampleData(audioTrack, encOut[eOut], mi);
                  }
                }
                encoder.releaseOutputBuffer(eOut, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encDone = true;
              }
            }
            audioPts += 1000000;
            decoder.stop(); decoder.release();
            encoder.stop(); encoder.release();
            decEx.release();
          }
          try { muxer.stop(); } catch (Exception e) {}
          muxer.release();
          js("onProgress(100,'Selesai')");
          js("onDone('" + outPath + "')");
        } catch (Exception e) {
          js("onError('" + (e.getMessage() != null ? e.getMessage().replace("'", "") : "Gagal") + "')");
        }
      }).start();
    }
  }
          }
