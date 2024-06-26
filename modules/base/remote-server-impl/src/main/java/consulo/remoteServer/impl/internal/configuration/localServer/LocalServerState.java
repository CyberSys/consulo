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
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.remoteServer.configuration.ServerConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentSource;
import consulo.remoteServer.runtime.local.LocalRunner;
import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
public class LocalServerState<S extends ServerConfiguration, D extends DeploymentConfiguration> implements RunProfileState {
  @Nonnull
  private final LocalRunner<D> myLocalRunner;
  @Nonnull
  private final DeploymentSource mySource;
  @Nonnull
  private final D myConfiguration;
  @Nonnull
  private final ExecutionEnvironment myEnvironment;

  public LocalServerState(@Nonnull LocalRunner<D> localRunner,
                          @Nonnull DeploymentSource deploymentSource,
                          @Nonnull D deploymentConfiguration,
                          @Nonnull ExecutionEnvironment environment) {
    myLocalRunner = localRunner;
    mySource = deploymentSource;
    myConfiguration = deploymentConfiguration;
    myEnvironment = environment;
  }

  @jakarta.annotation.Nullable
  @Override
  public ExecutionResult execute(Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException {
    return myLocalRunner.execute(mySource, myConfiguration, myEnvironment, executor, runner);
  }
}
