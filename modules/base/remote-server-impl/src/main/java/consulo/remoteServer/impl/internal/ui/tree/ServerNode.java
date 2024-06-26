package consulo.remoteServer.impl.internal.ui.tree;

import consulo.execution.executor.Executor;
import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
public interface ServerNode {
  boolean isConnected();

  boolean isStopActionEnabled();
  void stopServer();

  boolean isStartActionEnabled(@Nonnull Executor executor);
  void startServer(@Nonnull Executor executor);

  boolean isDeployAllEnabled();
  void deployAll();

  void editConfiguration();
}
