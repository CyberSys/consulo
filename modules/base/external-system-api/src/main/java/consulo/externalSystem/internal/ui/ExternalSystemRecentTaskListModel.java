/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.externalSystem.internal.ui;

import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.execution.ExternalTaskExecutionInfo;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 3:28 PM
 */
public class ExternalSystemRecentTaskListModel extends DefaultListModel {

  @Nonnull
  private final ProjectSystemId myExternalSystemId;
  @Nonnull
  private final Project         myProject;

  public ExternalSystemRecentTaskListModel(@Nonnull ProjectSystemId externalSystemId, @Nonnull Project project) {
    myExternalSystemId = externalSystemId;
    myProject = project;
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  @SuppressWarnings("unchecked")
  public void setTasks(@Nonnull List<ExternalTaskExecutionInfo> tasks) {
    clear();
    List<ExternalTaskExecutionInfo> tasksToUse = new ArrayList<>(tasks);
    for (ExternalTaskExecutionInfo task : tasksToUse) {
      addElement(task);
    }
  }

  @SuppressWarnings("unchecked")
  public void setFirst(@Nonnull ExternalTaskExecutionInfo task) {
    insertElementAt(task, 0);
    for (int i = 1; i < size(); i++) {
      if (task.equals(getElementAt(i))) {
        remove(i);
        break;
      }
    }
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  @Nonnull
  public List<ExternalTaskExecutionInfo> getTasks() {
    List<ExternalTaskExecutionInfo> result = new ArrayList<>();
    for (int i = 0; i < size(); i++) {
      Object e = getElementAt(i);
      if (e instanceof ExternalTaskExecutionInfo externalTaskExecutionInfo) {
        result.add(externalTaskExecutionInfo);
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public void ensureSize(int elementsNumber) {
    int toAdd = elementsNumber - size();
    if (toAdd == 0) {
      return;
    }
    if(toAdd < 0) {
      removeRange(elementsNumber, size() - 1);
    }
    while (--toAdd >= 0) {
      addElement(new MyEmptyDescriptor());
    }
  }

  /**
   * Asks current model to remove all 'recent task info' entries which point to tasks from external project with the given path.
   * 
   * @param externalProjectPath  target external project's path
   */
  public void forgetTasksFrom(@Nonnull String externalProjectPath) {
    for (int i = size() - 1; i >= 0; i--) {
      Object e = getElementAt(i);
      if (e instanceof ExternalTaskExecutionInfo externalTaskExecutionInfo) {
        String path = externalTaskExecutionInfo.getSettings().getExternalProjectPath();
        if (externalProjectPath.equals(path)
            || externalProjectPath.equals(ExternalSystemApiUtil.getRootProjectPath(path, myExternalSystemId, myProject)))
        {
          removeElementAt(i);
        }
      }
    }
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  static class MyEmptyDescriptor {
  }
}
