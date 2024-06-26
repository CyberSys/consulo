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

package consulo.ide.impl.idea.execution.util;

import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.execution.ui.awt.ListTableWithButtons;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.table.ListTableModel;

final class PathMappingTable extends ListTableWithButtons<PathMappingSettings.PathMapping> {
  public PathMappingTable() {
    getTableView().getEmptyText().setText("No mappings");
  }

  @Override
  protected ListTableModel createListModel() {
    ColumnInfo local = new ElementsColumnInfoBase<PathMappingSettings.PathMapping>("Local path") {
      @Override
      public String valueOf(PathMappingSettings.PathMapping pathMapping) {
        return pathMapping.getLocalRoot();
      }

      @Override
      public boolean isCellEditable(PathMappingSettings.PathMapping pathMapping) {
        return canDeleteElement(pathMapping);
      }

      @Override
      public void setValue(PathMappingSettings.PathMapping pathMapping, String s) {
        if (s.equals(valueOf(pathMapping))) {
          return;
        }
        pathMapping.setLocalRoot(s);
        setModified();
      }

      @Override
      protected String getDescription(PathMappingSettings.PathMapping pathMapping) {
        return valueOf(pathMapping);
      }
    };

    ColumnInfo remote = new ElementsColumnInfoBase<PathMappingSettings.PathMapping>("Remote path") {
      @Override
      public String valueOf(PathMappingSettings.PathMapping pathMapping) {
        return pathMapping.getRemoteRoot();
      }

      @Override
      public boolean isCellEditable(PathMappingSettings.PathMapping pathMapping) {
        return canDeleteElement(pathMapping);
      }

      @Override
      public void setValue(PathMappingSettings.PathMapping pathMapping, String s) {
        if (s.equals(valueOf(pathMapping))) {
          return;
        }
        pathMapping.setRemoteRoot(s);
        setModified();
      }

      @Override
      protected String getDescription(PathMappingSettings.PathMapping pathMapping) {
        return valueOf(pathMapping);
      }
    };

    return new ListTableModel((new ColumnInfo[]{local, remote}));
  }


  public PathMappingSettings getPathMappingSettings() {
    return new PathMappingSettings(getElements());
  }

  @Override
  protected PathMappingSettings.PathMapping createElement() {
    return new PathMappingSettings.PathMapping();
  }

  @Override
  protected boolean isEmpty(PathMappingSettings.PathMapping element) {
    return StringUtil.isEmpty(element.getLocalRoot()) && StringUtil.isEmpty(element.getRemoteRoot());
  }

  @Override
  protected PathMappingSettings.PathMapping cloneElement(PathMappingSettings.PathMapping envVariable) {
    return envVariable.clone();
  }

  @Override
  protected boolean canDeleteElement(PathMappingSettings.PathMapping selection) {
    return true;
  }
}
