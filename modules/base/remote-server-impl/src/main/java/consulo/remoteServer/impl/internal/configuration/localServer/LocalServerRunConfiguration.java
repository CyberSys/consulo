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
package consulo.remoteServer.impl.internal.configuration.localServer;

import consulo.process.ExecutionException;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.component.persist.ComponentSerializationUtil;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.remoteServer.ServerType;
import consulo.remoteServer.configuration.ServerConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfigurator;
import consulo.remoteServer.configuration.deployment.DeploymentSource;
import consulo.remoteServer.configuration.deployment.DeploymentSourceType;
import consulo.remoteServer.runtime.local.LocalRunner;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.logging.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class LocalServerRunConfiguration<S extends ServerConfiguration, D extends DeploymentConfiguration> extends RunConfigurationBase {
  private static final Logger LOG = Logger.getInstance(LocalServerRunConfiguration.class);
  private static final String DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE = "type";
  @NonNls public static final String SETTINGS_ELEMENT = "settings";
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private final ServerType<S> myServerType;
  private final DeploymentConfigurator<D> myDeploymentConfigurator;
  private final LocalRunner myLocalRunner;

  private DeploymentSource myDeploymentSource;
  private D myDeploymentConfiguration;

  public LocalServerRunConfiguration(Project project,
                                     ConfigurationFactory factory,
                                     String name,
                                     ServerType<S> serverType,
                                     DeploymentConfigurator<D> deploymentConfigurator,
                                     LocalRunner localRunner) {
    super(project, factory, name);
    myServerType = serverType;
    myDeploymentConfigurator = deploymentConfigurator;
    myLocalRunner = localRunner;
  }

  @Nonnull
  public ServerType<S> getServerType() {
    return myServerType;
  }

  @Nonnull
  public DeploymentConfigurator<D> getDeploymentConfigurator() {
    return myDeploymentConfigurator;
  }

  @Nonnull
  @Override
  public SettingsEditor<LocalServerRunConfiguration> getConfigurationEditor() {
    return new LocalToServerSettingsEditor(myServerType, myDeploymentConfigurator, getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
    if (myDeploymentSource == null) {
      throw new ExecutionException("Deployment is not selected");
    }

    return new LocalServerState(myLocalRunner, myDeploymentSource, myDeploymentConfiguration, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
  }

  public DeploymentSource getDeploymentSource() {
    return myDeploymentSource;
  }

  public void setDeploymentSource(DeploymentSource deploymentSource) {
    myDeploymentSource = deploymentSource;
  }

  public D getDeploymentConfiguration() {
    return myDeploymentConfiguration;
  }

  public void setDeploymentConfiguration(D deploymentConfiguration) {
    myDeploymentConfiguration = deploymentConfiguration;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    ConfigurationState state = XmlSerializer.deserialize(element, ConfigurationState.class);
    myDeploymentSource = null;
    if (state != null) {
      Element deploymentTag = state.myDeploymentTag;
      if (deploymentTag != null) {
        String typeId = deploymentTag.getAttributeValue(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE);
        DeploymentSourceType<?> type = findDeploymentSourceType(typeId);
        if (type != null) {
          myDeploymentSource = type.load(deploymentTag, getProject());
          myDeploymentConfiguration = myDeploymentConfigurator.createDefaultConfiguration(myDeploymentSource);
          ComponentSerializationUtil.loadComponentState(myDeploymentConfiguration.getSerializer(), deploymentTag.getChild(SETTINGS_ELEMENT));
        }
        else {
          LOG.warn("Cannot load deployment source for '" + getName() + "' run configuration: unknown deployment type '" + typeId + "'");
        }
      }
    }
  }

  @Nullable
  private static DeploymentSourceType<?> findDeploymentSourceType(@Nullable String id) {
    for (DeploymentSourceType<?> type : DeploymentSourceType.EP_NAME.getExtensionList()) {
      if (type.getId().equals(id)) {
        return type;
      }
    }
    return null;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    ConfigurationState state = new ConfigurationState();
    if (myDeploymentSource != null) {
      DeploymentSourceType type = myDeploymentSource.getType();
      Element deploymentTag = new Element("deployment").setAttribute(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE, type.getId());
      type.save(myDeploymentSource, deploymentTag);
      if (myDeploymentConfiguration != null) {
        Object configurationState = myDeploymentConfiguration.getSerializer().getState();
        if (configurationState != null) {
          Element settingsTag = new Element(SETTINGS_ELEMENT);
          XmlSerializer.serializeInto(configurationState, settingsTag, SERIALIZATION_FILTERS);
          deploymentTag.addContent(settingsTag);
        }
      }
      state.myDeploymentTag = deploymentTag;
    }
    XmlSerializer.serializeInto(state, element, SERIALIZATION_FILTERS);
    super.writeExternal(element);
  }

  public static class ConfigurationState {
    @Tag("deployment")
    public Element myDeploymentTag;
  }
}
