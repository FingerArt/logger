package com.orhanobut.logger;

import static com.orhanobut.logger.Utils.checkNotNull;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Abstract class that takes care of background threading the file log operation on Android.
 * implementing classes are free to directly perform I/O operations there.
 * <p>
 * Writes all logs to the disk with CSV format.
 */
public class DiskLogStrategy implements LogStrategy {

  @NonNull private final Handler handler;

  public DiskLogStrategy(@NonNull Handler handler) {
    this.handler = checkNotNull(handler);
  }

  @Override public void log(int level, @Nullable String tag, @NonNull String message) {
    checkNotNull(message);

    // do nothing on the calling thread, simply pass the tag/msg to the background thread
    handler.sendMessage(handler.obtainMessage(level, message));
  }

  static class WriteHandler extends Handler {

    @NonNull private final String folder;
    private final long maxFileSize;
    private File currentLogFile;
    private FileWriter logWriter;

    WriteHandler(@NonNull Looper looper, @NonNull String folder, long maxFileSize) {
      super(checkNotNull(looper));
      this.folder = checkNotNull(folder);
      this.maxFileSize = maxFileSize;
    }

    @SuppressWarnings("checkstyle:emptyblock")
    @Override public void handleMessage(@NonNull Message msg) {
      String content = (String) msg.obj;

      FileWriter fileWriter = null;

      try {
        fileWriter = findLogWriter(content.length());

        writeLog(fileWriter, content);

        fileWriter.flush();
      } catch (IOException e) {
        if (fileWriter != null) {
          try {
            fileWriter.close();
          } catch (IOException e1) { /* fail silently */ }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    /**
     * This is always called on a single background thread.
     * Implementing classes must ONLY write to the fileWriter and nothing more.
     * The abstract class takes care of everything else including close the stream and catching IOException
     *
     * @param fileWriter an instance of FileWriter already initialised to the correct file
     */
    private void writeLog(@NonNull FileWriter fileWriter, @NonNull String content) throws IOException {
      checkNotNull(fileWriter);
      checkNotNull(content);

      fileWriter.append(content);
    }

    @NonNull private FileWriter findLogWriter(long newContentLength) throws IOException {
      if (currentLogFile != null && currentLogFile.length() + newContentLength > maxFileSize) {
        logWriter.close();
        logWriter = null;
        currentLogFile = null;
      }

      try {
        if (currentLogFile == null) {
          currentLogFile = getNewLogFile(folder, "logs");
          logWriter = new FileWriter(currentLogFile, true);
        }
      } catch (IOException e) {
        currentLogFile = null;
        logWriter = null;
        throw e;
      }

      return logWriter;
    }

    private File getNewLogFile(@NonNull String folderName, @NonNull String fileName) {
      checkNotNull(folderName);
      checkNotNull(fileName);

      File folder = new File(folderName);
      if (!folder.exists()) {
        //TODO: What if folder is not created, what happens then?
        folder.mkdirs();
      }

      int newFileCount = 0;
      File newFile;
      File existingFile = null;

      newFile = new File(folder, String.format("%s_%s.csv", fileName, newFileCount));
      while (newFile.exists()) {
        existingFile = newFile;
        newFileCount++;
        newFile = new File(folder, String.format("%s_%s.csv", fileName, newFileCount));
      }

      if (existingFile != null) {
        if (existingFile.length() >= maxFileSize) {
          return newFile;
        }
        return existingFile;
      }

      return newFile;
    }
  }
}
