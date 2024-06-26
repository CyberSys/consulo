package consulo.remoteServer.impl.internal.ui;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.awt.tree.Tree;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * This is a temporary solution to integrate JavaEE based application servers into common Remote Servers/Clouds view. It should be removed
 * when remote app servers will be migrated to use remote-servers-api
 *
 * @author nik
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class RemoteServersViewContributor {
  public static final ExtensionPointName<RemoteServersViewContributor> EP_NAME = ExtensionPointName.create(RemoteServersViewContributor.class);

  public abstract boolean canContribute(@Nonnull Project project);

  public abstract void setupAvailabilityListener(@Nonnull Project project, @Nonnull Runnable checkAvailability);

  public abstract void setupTree(Project project, Tree tree, TreeBuilderBase builder);

  @Nonnull
  public abstract List<AbstractTreeNode<?>> createServerNodes(Project project);

  @Nullable
  public abstract Object getData(@Nonnull Key<?> dataId, @Nonnull ServersToolWindowContent content);
}
