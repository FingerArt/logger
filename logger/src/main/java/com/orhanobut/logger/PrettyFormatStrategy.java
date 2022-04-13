package com.orhanobut.logger;

import static com.orhanobut.logger.Utils.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws borders around the given log message along with additional information such as :
 *
 * <ul>
 *   <li>Thread information</li>
 *   <li>Method stack trace</li>
 * </ul>
 *
 * <pre>
 *  ┌──────────────────────────
 *  │ Method stack history
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 *  │ Thread information
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 *  │ Log message
 *  └──────────────────────────
 * </pre>
 *
 * <h3>Customize</h3>
 * <pre><code>
 *   FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
 *       .showThreadInfo(false)  // (Optional) Whether to show thread info or not. Default true
 *       .methodCount(0)         // (Optional) How many method line to show. Default 2
 *       .methodOffset(7)        // (Optional) Hides internal method calls up to offset. Default 5
 *       .logStrategy(customLog) // (Optional) Changes the log strategy to print out. Default LogCat
 *       .tag("My custom tag")   // (Optional) Global tag for every log. Default PRETTY_LOGGER
 *       .build();
 * </code></pre>
 */
public class PrettyFormatStrategy implements FormatStrategy {

  /**
   * Android's max limit for a log entry is ~4076 bytes,
   * so 4000 bytes is used as chunk size since default charset
   * is UTF-8
   */
  private static final int CHUNK_SIZE = 4000;

  /**
   * The minimum stack trace index, starts at this class after two native calls.
   */
  private static final int MIN_STACK_OFFSET = 5;

  /**
   * Drawing toolbox
   */
  private static final char TOP_LEFT_CORNER = '┌';
  private static final char BOTTOM_LEFT_CORNER = '└';
  private static final char MIDDLE_CORNER = '├';
  private static final char HORIZONTAL_LINE = '│';
  private static final String DOUBLE_DIVIDER = "────────────────────────────────────────────────────────";
  private static final String SINGLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄";
  private static final String TOP_BORDER = TOP_LEFT_CORNER + DOUBLE_DIVIDER + DOUBLE_DIVIDER;
  private static final String BOTTOM_BORDER = BOTTOM_LEFT_CORNER + DOUBLE_DIVIDER + DOUBLE_DIVIDER;
  private static final String MIDDLE_BORDER = MIDDLE_CORNER + SINGLE_DIVIDER + SINGLE_DIVIDER;
  private static final String NEW_LINE = System.getProperty("line.separator");

  private final int methodCount;
  private final int methodOffset;
  private final boolean showThreadInfo;
  @NonNull private final LogStrategy logStrategy;
  @Nullable private final String tag;

  private PrettyFormatStrategy(@NonNull Builder builder) {
    checkNotNull(builder);

    methodCount = builder.methodCount;
    methodOffset = builder.methodOffset;
    showThreadInfo = builder.showThreadInfo;
    logStrategy = builder.logStrategy;
    tag = builder.tag;
  }

  @NonNull public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public void log(int priority, @Nullable String onceOnlyTag, @NonNull String message) {
    StringBuilder logBuilder = new StringBuilder(" ");
    logBuilder.append(NEW_LINE);

    String tag = formatTag(onceOnlyTag);

    logBuilder.append(logTopBorder(priority, tag));
    logBuilder.append(logHeaderContent(priority, tag, methodCount));

    //get bytes of message with system's default charset (which is UTF-8 for Android)
    byte[] bytes = message.getBytes();
    int length = bytes.length;
    if (length <= CHUNK_SIZE) {
      if (methodCount > 0) {
        logBuilder.append(logDivider(priority, tag));
      }
      logBuilder.append(logContent(priority, tag, message));
      logBuilder.append(logBottomBorder(priority, tag));
      logStrategy.log(priority, tag, logBuilder.toString());
      return;
    }

    if (methodCount > 0) {
      logBuilder.append(logDivider(priority, tag));
    }
    for (int i = 0; i < length; i += CHUNK_SIZE) {
      StringBuilder chunkBuilder = new StringBuilder(logBuilder);
      int count = Math.min(length - i, CHUNK_SIZE);
      //create a new String with system's default charset (which is UTF-8 for Android)
      chunkBuilder.append(logContent(priority, tag, new String(bytes, i, count)));
      chunkBuilder.append(logBottomBorder(priority, tag));
      logStrategy.log(priority, tag, chunkBuilder.toString());
    }
  }

  private String logTopBorder(int logType, @Nullable String tag) {
    return logChunk(logType, tag, TOP_BORDER);
  }

  private StringBuilder logHeaderContent(int logType, @Nullable String tag, int methodCount) {
    StringBuilder builder = new StringBuilder();

    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    if (showThreadInfo) {
      builder.append(logChunk(logType, tag, HORIZONTAL_LINE + " Thread: " + Thread.currentThread().getName()));
      builder.append(logDivider(logType, tag));
    }
    String level = "";

    int stackOffset = getStackOffset(trace) + methodOffset;

    //corresponding method count with the current stack may exceeds the stack trace. Trims the count
    if (methodCount + stackOffset > trace.length) {
      methodCount = trace.length - stackOffset - 1;
    }

    for (int i = methodCount; i > 0; i--) {
      int stackIndex = i + stackOffset;
      if (stackIndex >= trace.length) {
        continue;
      }
      builder.append(HORIZONTAL_LINE)
          .append(' ')
          .append(level)
          .append(getSimpleClassName(trace[stackIndex].getClassName()))
          .append(".")
          .append(trace[stackIndex].getMethodName())
          .append(" ")
          .append(" (")
          .append(trace[stackIndex].getFileName())
          .append(":")
          .append(trace[stackIndex].getLineNumber())
          .append(")")
          .append("\n");
      level += "   ";
    }
    return builder;
  }

  private String logBottomBorder(int logType, @Nullable String tag) {
    return logChunk(logType, tag, BOTTOM_BORDER);
  }

  private String logDivider(int logType, @Nullable String tag) {
    return logChunk(logType, tag, MIDDLE_BORDER);
  }

  private CharSequence logContent(int logType, @Nullable String tag, @NonNull String chunk) {
    StringBuilder builder = new StringBuilder();
    String[] lines = chunk.split(NEW_LINE);
    for (String line : lines) {
      builder.append(logChunk(logType, tag, HORIZONTAL_LINE + " " + line));
    }
    return builder;
  }

  private String logChunk(int priority, @Nullable String tag, @NonNull String chunk) {
    return chunk + NEW_LINE;
  }


  private String getSimpleClassName(@NonNull String name) {
    checkNotNull(name);

    int lastIndex = name.lastIndexOf(".");
    return name.substring(lastIndex + 1);
  }

  /**
   * Determines the starting index of the stack trace, after method calls made by this class.
   *
   * @param trace the stack trace
   * @return the stack offset
   */
  private int getStackOffset(@NonNull StackTraceElement[] trace) {
    checkNotNull(trace);

    for (int i = MIN_STACK_OFFSET; i < trace.length; i++) {
      StackTraceElement e = trace[i];
      String name = e.getClassName();
      if (!name.equals(LoggerPrinter.class.getName()) && !name.equals(Logger.class.getName())) {
        return --i;
      }
    }
    return -1;
  }

  @Nullable private String formatTag(@Nullable String tag) {
    if (!Utils.isEmpty(tag) && !Utils.equals(this.tag, tag)) {
      return this.tag + "-" + tag;
    }
    return this.tag;
  }

  public static class Builder {
    int methodCount = 2;
    int methodOffset = 0;
    boolean showThreadInfo = true;
    @Nullable LogStrategy logStrategy;
    @Nullable String tag = "PRETTY_LOGGER";

    private Builder() {
    }

    @NonNull public Builder methodCount(int val) {
      methodCount = val;
      return this;
    }

    @NonNull public Builder methodOffset(int val) {
      methodOffset = val;
      return this;
    }

    @NonNull public Builder showThreadInfo(boolean val) {
      showThreadInfo = val;
      return this;
    }

    @NonNull public Builder logStrategy(@Nullable LogStrategy val) {
      logStrategy = val;
      return this;
    }

    @NonNull public Builder tag(@Nullable String tag) {
      this.tag = tag;
      return this;
    }

    @NonNull public PrettyFormatStrategy build() {
      if (logStrategy == null) {
        logStrategy = new LogcatLogStrategy();
      }
      return new PrettyFormatStrategy(this);
    }
  }

}
