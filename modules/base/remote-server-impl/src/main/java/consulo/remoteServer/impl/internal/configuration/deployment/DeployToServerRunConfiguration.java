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
package consulo.remoteServer.impl.internal.configuration.deployment;

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
import consulo.remoteServer.configuration.deployment.DeploymentConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfigurator;
import consulo.remoteServer.configuration.deployment.DeploymentSource;
import consulo.remoteServer.configuration.deployment.DeploymentSourceType;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.remoteServer.ServerType;
import consulo.remoteServer.configuration.RemoteServer;
import consulo.remoteServer.configuration.RemoteServersManager;
import consulo.remoteServer.configuration.ServerConfiguration;
import consulo.remoteServer.impl.internal.runtime.DeployToServerState;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.logging.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class DeployToServerRunConfiguration<S extends ServerConfiguration, D extends DeploymentConfiguration> extends RunConfigurationBase {
  private static final Logger LOG = Logger.getInstance(DeployToServerRunConfiguration.class);
  private static final String DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE = "type";
  @NonNls public static final String SETTINGS_ELEMENT = "settings";
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private final ServerType<S> myServerType;
  private final DeploymentConfigurator<D> myDeploymentConfigurator;
  private String myServerName;
  private DeploymentSource myDeploymentSource;
  private D myDeploymentConfiguration;

  public DeployToServerRunConfiguration(Project project, ConfigurationFactory factory, String name, ServerType<S> serverType, DeploymentConfigurator<D> deploymentConfigurator) {
    super(project, factory, name);
    myServerType = serverType;
    myDeploymentConfigurator = deploymentConfigurator;
  }

  @Nonnull
  public ServerType<S> getServerType() {
    return myServerType;
  }

  public String getServerName() {
    return myServerName;
  }

  @Nonnull
  public DeploymentConfigurator<D> getDeploymentConfigurator() {
    return myDeploymentConfigurator;
  }

  @Nonnull
  @Override
  public SettingsEditor<DeployToServerRunConfiguration> getConfigurationEditor() {
    return new DeployToServerSettingsEditor(myServerType, myDeploymentConfigurator, getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
    String serverName = getServerName();
    if (serverName == null) {
      throw new ExecutionException("Server is not specified");
    }

    RemoteServer<S> server = RemoteServersManager.getInstance().findByName(serverName, myServerType);
    if (server == null) {
      throw new ExecutionException("Server '" + serverName + " not found");
    }

    if (myDeploymentSource == null) {
      throw new ExecutionException("Deployment is not selected");
    }

    return new DeployToServerState(server, myDeploymentSource, myDeploymentConfiguration, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
  }

  public void setServerName(String serverName) {
    myServerName = serverName;
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
    myServerName =  null;
    myDeploymentSource = null;
    if (state != null) {
      myServerName = state.myServerName;
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
    state.myServerName = myServerName;
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
    @Attribute("server-name")
    public String myServerName;

    @Tag("deployment")
    public Element myDeploymentTag;
  }
}
