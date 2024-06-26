package consulo.remoteServer.impl.internal.ui.tree.action;

import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.remoteServer.impl.internal.ui.ServersToolWindowContent;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public abstract class ServersTreeActionBase extends AnAction {
  protected ServersTreeActionBase(String text, String description, Image icon) {
    super(text, description, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    ServersToolWindowContent content = e.getData(ServersToolWindowContent.KEY);
    e.getPresentation().setEnabled(content != null && isEnabled(content, e));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ServersToolWindowContent content = e.getData(ServersToolWindowContent.KEY);
    if (content == null) {
      return;
    }
    doActionPerformed(content);
  }

  protected abstract boolean isEnabled(@Nonnull ServersToolWindowContent content, AnActionEvent e);

  protected abstract void doActionPerformed(@Nonnull ServersToolWindowContent content);
}
