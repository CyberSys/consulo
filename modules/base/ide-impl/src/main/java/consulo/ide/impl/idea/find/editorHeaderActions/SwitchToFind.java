package consulo.ide.impl.idea.find.editorHeaderActions;

import consulo.ide.impl.idea.find.EditorSearchSession;
import consulo.find.FindModel;
import consulo.ide.impl.idea.find.FindUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.application.dumb.DumbAware;
import jakarta.annotation.Nonnull;

import javax.swing.*;

import static consulo.language.editor.CommonDataKeys.EDITOR;

/**
 * Created by IntelliJ IDEA.
 * User: zajac
 * Date: 05.03.11
 * Time: 10:57
 * To change this template use File | Settings | File Templates.
 */
public class SwitchToFind extends AnAction implements DumbAware {
  public SwitchToFind(@Nonnull JComponent shortcutHolder) {
    AnAction findAction = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND);
    if (findAction != null) {
      registerCustomShortcutSet(findAction.getShortcutSet(), shortcutHolder);
    }
  }

  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent e) {
    if (KeymapUtil.isEmacsKeymap()) {
      // Emacs users are accustomed to the editor that executes 'find next' on subsequent pressing of shortcut that
      // activates 'incremental search'. Hence, we do the similar hack here for them.
      ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).update(e);
    }
    else {
      EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
      e.getPresentation().setEnabledAndVisible(search != null);
    }
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    if (KeymapUtil.isEmacsKeymap()) {
      // Emacs users are accustomed to the editor that executes 'find next' on subsequent pressing of shortcut that
      // activates 'incremental search'. Hence, we do the similar hack here for them.
      ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).actionPerformed(e);
      return;
    }

    EditorSearchSession search = e.getRequiredData(EditorSearchSession.SESSION_KEY);
    final FindModel findModel = search.getFindModel();
    FindUtil.configureFindModel(false, e.getDataContext().getData(EDITOR), findModel, false);
    search.getComponent().selectSearchAll();
  }
}
