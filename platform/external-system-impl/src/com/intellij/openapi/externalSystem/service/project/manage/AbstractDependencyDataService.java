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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 4/14/13 11:21 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public abstract class AbstractDependencyDataService<T extends AbstractDependencyData<?>> implements ProjectDataService<T> {

  public void removeData(@NotNull Collection<ExportableOrderEntry> toRemove, @NotNull final Module module, boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    for (final ExportableOrderEntry dependency : toRemove) {
      ExternalSystemApiUtil.executeProjectChangeAction(module.getProject(), ProjectSystemId.IDE, toRemove, synchronous, new Runnable() {
        @Override
        public void run() {
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            // The thing is that intellij created order entry objects every time new modifiable model is created,
            // that's why we can't use target dependency object as is but need to get a reference to the current
            // entry object from the model instead.
            for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
              if (entry.getPresentableName().equals(dependency.getPresentableName())) {
                moduleRootModel.removeOrderEntry(entry);
                break;
              }
            }
          }
          finally {
            moduleRootModel.commit();
          }
        }
      });
    }
  }

  public void setScope(@NotNull final DependencyScope scope, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    Project project = dependency.getOwnerModule().getProject();
    ExternalSystemApiUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, dependency, synchronous, new Runnable() {
      @Override
      public void run() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setScope(scope);
          }
        });
      }
    });
  }

  public void setExported(final boolean exported, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    Project project = dependency.getOwnerModule().getProject();
    ExternalSystemApiUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, dependency, synchronous, new Runnable() {
      @Override
      public void run() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setExported(exported);
          }
        });
      }
    });
  }
  
  private static void doForDependency(@NotNull ExportableOrderEntry entry, @NotNull Consumer<ExportableOrderEntry> consumer) {
    // We need to get an up-to-date modifiable model to work with.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(entry.getOwnerModule());
    final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
    try {
      // The thing is that intellij created order entry objects every time new modifiable model is created,
      // that's why we can't use target dependency object as is but need to get a reference to the current
      // entry object from the model instead.
      for (OrderEntry e : moduleRootModel.getOrderEntries()) {
        if (e instanceof ExportableOrderEntry && e.getPresentableName().equals(entry.getPresentableName())) {
          consumer.consume((ExportableOrderEntry)e);
          break;
        }
      }
    }
    finally {
      moduleRootModel.commit();
    }
  }
}