package com.intellij.xdebugger.impl.settings;

import com.intellij.util.containers.ContainerUtil;
import consulo.configurable.Configurable;
import consulo.debugger.impl.setting.XDebuggerGeneralConfigurable;
import consulo.execution.debug.setting.DebuggerSettingsCategory;
import consulo.execution.debug.setting.XDebuggerSettings;
import consulo.util.collection.SmartList;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public class XDebuggerConfigurableProvider {
  @Nonnull
  public static Collection<Configurable> getConfigurables(@Nonnull DebuggerSettingsCategory category) {
    List<Configurable> list;
    if (category == DebuggerSettingsCategory.GENERAL) {
      list = new SmartList<>(new XDebuggerGeneralConfigurable());
    }
    else {
      list = null;
    }

    for (XDebuggerSettings<?> settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
      Collection<? extends Configurable> configurables = settings.createConfigurables(category);
      if (!configurables.isEmpty()) {
        if (list == null) {
          list = new SmartList<>();
        }
        list.addAll(configurables);
      }
    }
    return ContainerUtil.notNullize(list);
  }

  public static void generalApplied(@Nonnull DebuggerSettingsCategory category) {
    for (XDebuggerSettings<?> settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
      settings.generalApplied(category);
    }
  }
}