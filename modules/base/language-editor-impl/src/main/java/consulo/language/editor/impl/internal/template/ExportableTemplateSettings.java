/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.language.editor.impl.internal.template;

import consulo.component.persist.*;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

@Singleton
@SuppressWarnings("deprecation")
@Deprecated
@State(
  name = "ExportableTemplateSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/template.settings.xml", roamingType = RoamingType.DISABLED)
)
public final class ExportableTemplateSettings implements PersistentStateComponent<ExportableTemplateSettings> {
  public Collection<TemplateSettingsImpl.TemplateKey> deletedKeys = new SmartList<TemplateSettingsImpl.TemplateKey>();

  @Nullable
  @Override
  public ExportableTemplateSettings getState() {
    return this;
  }

  @Override
  public void loadState(ExportableTemplateSettings state) {
    TemplateSettingsImpl templateSettings = TemplateSettingsImpl.getInstanceImpl();
    List<TemplateSettingsImpl.TemplateKey> deletedTemplates = templateSettings.getDeletedTemplates();
    deletedTemplates.clear();
    deletedTemplates.addAll(state.deletedKeys);
    templateSettings.applyNewDeletedTemplates();
  }
}
