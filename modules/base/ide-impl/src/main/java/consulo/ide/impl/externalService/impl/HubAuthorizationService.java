/*
 * Copyright 2013-2021 consulo.io
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
package consulo.ide.impl.externalService.impl;

import com.google.gson.Gson;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.builtinWebServer.BuiltInServerManager;
import consulo.externalService.ExternalServiceConfiguration;
import consulo.http.HttpRequests;
import consulo.ide.impl.external.api.UserAccount;
import consulo.ide.impl.idea.ide.BrowserUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.ModalityState;
import consulo.ui.image.Image;
import consulo.util.collection.impl.map.ConcurrentHashMap;
import consulo.util.concurrent.AsyncResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author VISTALL
 * @since 09/10/2021
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class HubAuthorizationService {
  private static final Logger LOG = Logger.getInstance(HubAuthorizationService.class);

  public static class OAuthRequestResult {
    public UserAccount userAccount;
    public String token;
  }

  public static class CallbackInfo {
    public boolean modal;

    public AsyncResult<Void> result = AsyncResult.undefined();
  }

  private final BuiltInServerManager myBuiltInServerManager;
  private final Provider<ExternalServiceConfiguration> myExternalServiceConfigurationProvider;

  private Map<String, CallbackInfo> myCallbacks = new ConcurrentHashMap<>();

  @Inject
  public HubAuthorizationService(BuiltInServerManager builtInServerManager,
                                 Provider<ExternalServiceConfiguration> externalServiceConfigurationProvider) {
    myBuiltInServerManager = builtInServerManager;
    myExternalServiceConfigurationProvider = externalServiceConfigurationProvider;
  }

  @Nullable
  public String getEmail() {
    return myExternalServiceConfigurationProvider.get().getEmail();
  }

  @Nullable
  public Image getUserIcon() {
    return myExternalServiceConfigurationProvider.get().getUserIcon();
  }

  public void reset() {
    ExternalServiceConfigurationImpl configuration = (ExternalServiceConfigurationImpl)myExternalServiceConfigurationProvider.get();

    configuration.reset();
  }

  @Nonnull
  public AsyncResult<Void> openLinkSite(boolean modal) {
    String tokenForAuth = RandomStringUtils.randomAlphabetic(48);

    CallbackInfo info = myCallbacks.computeIfAbsent(tokenForAuth, s -> new CallbackInfo());
    info.modal = modal;

    int localPort = myBuiltInServerManager.getPort();

    StringBuilder builder = new StringBuilder(WebServiceApi.LINK_CONSULO.buildUrl());
    builder.append("?");
    builder.append("token=").append(tokenForAuth).append("&");
    builder.append("host=").append(URLEncoder.encode(getHostName(), StandardCharsets.UTF_8)).append("&");

    String redirectUrl = "http://localhost:" + localPort + "/redirectAuth";
    redirectUrl = redirectUrl.replace("&", "%26");
    redirectUrl = redirectUrl.replace("/", "%2F");
    redirectUrl = redirectUrl.replace(":", "%3A");

    builder.append("redirect=").append(redirectUrl);

    BrowserUtil.browse(builder.toString());

    return info.result;
  }

  public void doGetToken(String sharedToken) {
    CallbackInfo info = myCallbacks.remove(sharedToken);

    LocalizeValue titleValue = LocalizeValue.localizeTODO("Requesting oauth token...");
    if (info != null && info.modal) {
      Application application = Application.get();
      application.invokeLater(() -> {
        ModalityState currentModalityState = application.getCurrentModalityState();

        application.invokeLater(() -> {
          Task.Modal.queue(null, titleValue, indicator -> doGetTokenProgress(indicator, sharedToken, info));
        }, currentModalityState);
      }, application.getAnyModalityState());
    }
    else {
      Task.Backgroundable.queue(null, titleValue, indicator -> doGetTokenProgress(indicator, sharedToken, info));
    }
  }

  private void doGetTokenProgress(ProgressIndicator indicator, String sharedToken, @Nullable CallbackInfo info) {
    try {
      String json = HttpRequests.request(WebServiceApi.OAUTH_API.buildUrl("request?token=" + sharedToken)).readString(indicator);

      OAuthRequestResult requestResult = new Gson().fromJson(json, OAuthRequestResult.class);

      ExternalServiceConfigurationImpl externalServiceConfiguration =
        (ExternalServiceConfigurationImpl)myExternalServiceConfigurationProvider.get();

      externalServiceConfiguration.authorize(requestResult.userAccount.username, requestResult.token);

      externalServiceConfiguration.updateIcon();

      if (info != null) {
        info.result.setDone();
      }
    }
    catch (IOException e) {
      LOG.warn(e);

      if (info != null) {
        info.result.rejectWithThrowable(e);
      }
    }
  }

  private static String getHostName() {
    String hostname = "Unknown";

    try {
      InetAddress addr;
      addr = InetAddress.getLocalHost();
      hostname = addr.getHostName();
    }
    catch (UnknownHostException ignored) {
    }

    return hostname;
  }
}