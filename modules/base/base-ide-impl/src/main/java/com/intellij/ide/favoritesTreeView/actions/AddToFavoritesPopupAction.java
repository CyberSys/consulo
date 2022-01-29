/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import consulo.dataContext.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import consulo.project.Project;
import javax.annotation.Nonnull;

import java.util.List;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class AddToFavoritesPopupAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @Nonnull DefaultActionGroup group, @Nonnull DataContext dataContext) {
    group.removeAll();

    final List<String> availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesListNames();
    availableFavoritesLists.remove(dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY));

    for (String favoritesList : availableFavoritesLists) {
      group.add(new AddToFavoritesAction(favoritesList));
    }
    group.add(new AddToNewFavoritesListAction());
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }
}
