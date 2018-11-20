package net.hockeyapp.android;

import android.app.Activity;
import android.util.Log;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

public class NativeCrashManager {
  public static void handleDumpFiles(Activity activity, String identifier) {
    String[] filenames = searchForDumpFiles();
    for (String dumpFilename : filenames) {
      String logFilename = createLogFile();
      if (logFilename != null) {
        uploadDumpAndLog(activity, identifier, dumpFilename, logFilename);
      }
    }
  }
  
  public static String createLogFile() {
    final Date now = new Date();

    try {
      // Create filename from a random uuid
      String filename = UUID.randomUUID().toString();
      String path = Constants.FILES_PATH + "/" + filename + ".faketrace";
      Log.d(Constants.TAG, "Writing unhandled exception to: " + path);
      
      // Write the stacktrace to disk
      BufferedWriter write = new BufferedWriter(new FileWriter(path));
      write.write("Package: " + Constants.APP_PACKAGE + "\n");
      write.write("Version Code: " + Constants.APP_VERSION + "\n");
      write.write("Android: " + Constants.ANDROID_VERSION + "\n");
      write.write("Manufacturer: " + Constants.PHONE_MANUFACTURER + "\n");
      write.write("Model: " + Constants.PHONE_MODEL + "\n");
      write.write("Date: " + now + "\n");
      write.write("\n");
      write.write("MinidumpContainer");
      write.flush();
      write.close();
      
      return filename + ".faketrace";
    } 
    catch (Exception another) {
    }
    
    return null;
  }
/*
  private static String multipost(String urlString, MultipartEntity reqEntity) {

    try {
      URL url = new URL(urlString);
      HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
      conn.setReadTimeout(10000);
      conn.setConnectTimeout(15000);
      conn.setRequestMethod("POST");
      conn.setUseCaches(false);
      conn.setDoInput(true);
      conn.setDoOutput(true);

      conn.setRequestProperty("Connection", "Keep-Alive");
      conn.addRequestProperty("Content-length", reqEntity.getContentLength()+"");
      conn.addRequestProperty(reqEntity.getContentType().getName(), reqEntity.getContentType().getValue());

      OutputStream os = conn.getOutputStream();
      reqEntity.writeTo(conn.getOutputStream());
      os.close();
      conn.connect();

      if (conn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
        return readStream(conn.getInputStream());
      }

    } catch (Exception e) {
      Log.e("MainActivity", "multipart post error " + e + "(" + urlString + ")");
    }
    return null;
  }
*/
  private static String readStream(InputStream in) {
    BufferedReader reader = null;
    StringBuilder builder = new StringBuilder();
    try {
      reader = new BufferedReader(new InputStreamReader(in));
      String line = "";
      while ((line = reader.readLine()) != null) {
        builder.append(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return builder.toString();
  }

  public static void uploadDumpAndLog(final Activity activity, final String identifier, final String dumpFilename, final String logFilename) {
    new Thread() {
      @Override
      public void run() {
        try {
          DefaultHttpClient httpClient = new DefaultHttpClient();
          HttpPost httpPost = new HttpPost("https://rink.hockeyapp.net/api/2/apps/" + identifier + "/crashes/upload");

          MultipartEntity entity = new MultipartEntity();

          File dumpFile = new File(Constants.FILES_PATH, dumpFilename);
          entity.addPart("attachment0", new FileBody(dumpFile));

          File logFile = new File(Constants.FILES_PATH, logFilename);
          entity.addPart("log", new FileBody(logFile));

          httpPost.setEntity(entity);

          httpClient.execute(httpPost);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        finally {
          activity.deleteFile(logFilename);
          activity.deleteFile(dumpFilename);
        }
      }
    }.start();
  } 
  
  private static String[] searchForDumpFiles() {
    if (Constants.FILES_PATH != null) {
      // Try to create the files folder if it doesn't exist
      File dir = new File(Constants.FILES_PATH + "/");
      boolean created = dir.mkdir();
      if (!created && !dir.exists()) {
        return new String[0];
      }
  
      // Filter for ".dmp" files
      FilenameFilter filter = new FilenameFilter() { 
        public boolean accept(File dir, String name) {
          return name.endsWith(".dmp"); 
        } 
      }; 
      return dir.list(filter);
    }
    else {
      Log.d(Constants.TAG, "Can't search for exception as file path is null.");
      return new String[0];
    }
  }
}
