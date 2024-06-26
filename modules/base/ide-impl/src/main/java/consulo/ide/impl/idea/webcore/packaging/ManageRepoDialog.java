/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.ide.impl.idea.webcore.packaging;

import consulo.application.ApplicationManager;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.repository.ui.PackageManagementService;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.awt.*;
import consulo.util.concurrent.AsyncResult;

import javax.swing.*;
import java.util.List;

public class ManageRepoDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private final JBList<String> myList;
  private boolean myEnabled;
  private static final Logger LOG = Logger.getInstance(ManageRepoDialog.class);

  public ManageRepoDialog(Project project, final PackageManagementService controller) {
    super(project, false);
    init();
    setTitle(IdeBundle.message("manage.repositories.dialog.title"));
    myList = new JBList<>();
    myList.setPaintBusy(true);
    final DefaultListModel<String> repoModel = new DefaultListModel<>();
    AsyncResult<List<String>> result = controller.fetchAllRepositories();
    result.doWhenDone(repoUrls -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (isDisposed()) return;
        myList.setPaintBusy(false);
        for (String repoUrl : repoUrls) {
          repoModel.addElement(repoUrl);
        }
      }, IdeaModalityState.any());
    });
    result.doWhenRejectedWithThrowable(e -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (isDisposed()) return;
        myList.setPaintBusy(false);
        LOG.warn(e);
      });
    });
    myList.setModel(repoModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(event -> {
      final String selected = myList.getSelectedValue();
      myEnabled = controller.canModifyRepository(selected);
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions();
    decorator.setAddActionName(IdeBundle.message("action.add.repository"));
    decorator.setRemoveActionName(IdeBundle.message("action.remove.repository.from.list"));
    decorator.setEditActionName(IdeBundle.message("action.edit.repository.url"));

    decorator.setAddAction(button -> {
      String url = Messages.showInputDialog(IdeBundle.message("please.input.repository.url"), IdeBundle.message("repository.url.title"), null);
      if (!StringUtil.isEmptyOrSpaces(url) && !repoModel.contains(url)) {
        repoModel.addElement(url);
        controller.addRepository(url);
      }
    });
    decorator.setEditAction(button -> {
      final String oldValue = myList.getSelectedValue();

      String url = Messages.showInputDialog(IdeBundle.message("please.edit.repository.url"), IdeBundle.message("repository.url.title"), null, oldValue, new InputValidator() {
        @Override
        public boolean checkInput(String inputString) {
          return !repoModel.contains(inputString);
        }

        @Override
        public boolean canClose(String inputString) {
          return true;
        }
      });
      if (!StringUtil.isEmptyOrSpaces(url) && !oldValue.equals(url)) {
        repoModel.addElement(url);
        repoModel.removeElement(oldValue);
        controller.removeRepository(oldValue);
        controller.addRepository(url);
      }
    });
    decorator.setRemoveAction(button -> {
      String selected = myList.getSelectedValue();
      controller.removeRepository(selected);
      repoModel.removeElement(selected);
      button.setEnabled(false);
    });
    decorator.setRemoveActionUpdater(e -> myEnabled);
    decorator.setEditActionUpdater(e -> myEnabled);

    final JPanel panel = decorator.createPanel();
    panel.setPreferredSize(JBUI.size(800, 600));
    myMainPanel.add(panel);

  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
