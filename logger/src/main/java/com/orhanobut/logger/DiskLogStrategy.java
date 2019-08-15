package com.orhanobut.logger;

import android.os.*;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static com.orhanobut.logger.Utils.checkNotNull;

/**
 * Abstract class that takes care of background threading the file log operation on Android.
 * implementing classes are free to directly perform I/O operations there.
 *
 * Writes all logs to the disk with CSV format.
 */
public class DiskLogStrategy implements LogStrategy, RemoveFile {
  @NonNull
  private final Handler handler;
  private final long MAX_SIZE;
  private final int MAX_HISTORY;

  public DiskLogStrategy(Builder builder) {
    checkNotNull(builder);
    handler = builder.handler;
    MAX_SIZE = builder.maxSize;
    MAX_HISTORY = builder.maxHistory;

    new Thread(new Runnable() {
      @Override public void run() {
        try {
          removeFileBySize();
          removeFileByHistory();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }).start();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public void removeFileBySize() throws ParseException {
    String diskPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    String folder = diskPath + File.separatorChar + "logger";
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Calendar limitCalendar = Calendar.getInstance();
    limitCalendar.add(Calendar.DAY_OF_YEAR, -MAX_HISTORY / 2);
    Date limitDate = limitCalendar.getTime();
    File folderFile = new File(folder);
    if (folderFile.exists() && Utils.sizeOfDirectory(folderFile)> MAX_SIZE) {
      File[] logFiles = folderFile.listFiles();
      for (File logfile : logFiles) {
        Date date = dateFormat.parse(logfile.getName());
        if (date.before(limitDate)) {
          logfile.delete();
        }
      }
    }

  }

  @Override
  public void removeFileByHistory() throws ParseException {
    String diskPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    String folder = diskPath + File.separatorChar + "logger";
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Calendar limitCalendar = Calendar.getInstance();
    limitCalendar.add(Calendar.DAY_OF_YEAR, -MAX_HISTORY);
    Date limitDate = limitCalendar.getTime();
    File folderFile = new File(folder);

    if (folderFile.exists()) {
      File[] logFiles = folderFile.listFiles();
      for (File logfile : logFiles) {
        Date date = dateFormat.parse(logfile.getName());
        if (date.before(limitDate)) {
          logfile.delete();
        }
      }
    }
  }

  public static class Builder {
    Handler handler;
    // folderSize Byte
    long maxSize = 1024 * 1024 * 1024;
    // fileSize Byte
    int maxBytes = 500 * 1024; // 500K averages to a 4000 lines per file
    // history day
    int maxHistory = 60;

    String folderName = "logger";

    public Builder setHandler() {
      String diskPath = Environment.getExternalStorageDirectory().getAbsolutePath();
      String folder = diskPath + File.separatorChar + "logger" + File.separatorChar + folderName;

      HandlerThread ht = new HandlerThread("AndroidFileLogger." + folder);
      ht.start();
      handler = new DiskLogStrategy.WriteHandler(ht.getLooper(), folder, this.maxBytes);
      return this;
    }

    public Builder setMaxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    public Builder setMaxBytes(int maxBytes) {
      this.maxBytes = maxBytes;
      return this;
    }


    public Builder setMaxHistory(int maxHistory) {
      this.maxHistory = maxHistory;
      return this;
    }

    public Builder setFolderName(String folderName) {
      this.folderName = folderName;
      return this;
    }


    public DiskLogStrategy build() {
      this.setHandler();
      return new DiskLogStrategy(this);
    }
  }

  @Override public void log(int level, @Nullable String tag, @NonNull String message) {
    checkNotNull(message);

    // do nothing on the calling thread, simply pass the tag/msg to the background thread
    handler.sendMessage(handler.obtainMessage(level, message));
  }

  static class WriteHandler extends Handler {

    @NonNull private final String folder;
    private final int maxFileSize;

    WriteHandler(@NonNull Looper looper, @NonNull String folder, int maxFileSize) {
      super(checkNotNull(looper));
      this.folder = checkNotNull(folder);
      this.maxFileSize = maxFileSize;
    }

    @SuppressWarnings("checkstyle:emptyblock")
    @Override public void handleMessage(@NonNull Message msg) {
      String content = (String) msg.obj;

      FileWriter fileWriter = null;
      Date now = new Date();
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

      String date = dateFormat.format(now);
      File logFile = getLogFile(folder, date);

      try {
        fileWriter = new FileWriter(logFile, true);

        writeLog(fileWriter, content);

        fileWriter.flush();
        fileWriter.close();
      } catch (IOException e) {
        if (fileWriter != null) {
          try {
            fileWriter.flush();
            fileWriter.close();
          } catch (IOException e1) { /* fail silently */ }
        }
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

    private File getLogFile(@NonNull String folderName, @NonNull String fileName) {
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
