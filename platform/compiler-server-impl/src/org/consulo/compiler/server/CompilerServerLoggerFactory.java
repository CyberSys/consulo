package org.consulo.compiler.server;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author VISTALL
 * @since 21:45/03.10.13
 */
public class CompilerServerLoggerFactory implements Logger.Factory {
  @Override
  public Logger getLoggerInstance(String category) {
    return new Logger() {
      @Override
      public boolean isDebugEnabled() {
        return false;
      }

      @Override
      public void debug(@NonNls String message) {

      }

      @Override
      public void debug(@Nullable Throwable t) {

      }

      @Override
      public void debug(@NonNls String message, @Nullable Throwable t) {

      }

      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls String... details) {
        System.out.println(message);
        if (t != null) {
          t.printStackTrace();
        }
      }

      @Override
      public void info(@NonNls String message) {
        System.out.println(message);
      }

      @Override
      public void info(@NonNls String message, @Nullable Throwable t) {
        System.out.println(message);
        if (t != null) {
          t.printStackTrace();
        }
      }

      @Override
      public void warn(@NonNls String message, @Nullable Throwable t) {
        System.out.println(message);
        if (t != null) {
          t.printStackTrace();
        }
      }

      @Override
      public void setLevel(Level level) {
      }
    };
  }
}