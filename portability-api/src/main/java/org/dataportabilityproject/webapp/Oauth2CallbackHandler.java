/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataportabilityproject.webapp;

import static org.apache.axis.transport.http.HTTPConstants.HEADER_HOST;
import static org.apache.axis.transport.http.HTTPConstants.HEADER_LOCATION;

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.Map;
import org.dataportabilityproject.ServiceProviderRegistry;
import org.dataportabilityproject.cloud.interfaces.Metrics;
import org.dataportabilityproject.cloud.interfaces.Metrics.Kind;
import org.dataportabilityproject.cloud.interfaces.Metrics.LabelDescriptor;
import org.dataportabilityproject.cloud.interfaces.Metrics.ValueType;
import org.dataportabilityproject.job.JobDao;
import org.dataportabilityproject.job.JobUtils;
import org.dataportabilityproject.job.PortabilityJob;
import org.dataportabilityproject.shared.Config.Environment;
import org.dataportabilityproject.shared.PortableDataType;
import org.dataportabilityproject.shared.ServiceMode;
import org.dataportabilityproject.shared.auth.AuthData;
import org.dataportabilityproject.shared.auth.OnlineAuthDataGenerator;
import org.dataportabilityproject.shared.settings.CommonSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpHandler for callbacks from Oauth2 authorization flow.
 */
final class Oauth2CallbackHandler implements HttpHandler {
  private static final String TRANSFER_METRIC_TYPE = "transfer";
  private static final String TRANSFER_METRIC_SERVICE_MODE_LABEL = "serviceMode";
  private static final String TRANSFER_METRIC_SERVICE_LABEL = "service";

  private final Logger logger = LoggerFactory.getLogger(Oauth2CallbackHandler.class);

  private final ServiceProviderRegistry serviceProviderRegistry;
  private final JobDao jobDao;
  private final CryptoHelper cryptoHelper;
  private final Metrics metrics;
  private final CommonSettings commonSettings;

  @Inject
  Oauth2CallbackHandler(
      ServiceProviderRegistry serviceProviderRegistry,
      JobDao jobDao,
      CryptoHelper cryptoHelper,
      Metrics metrics,
      CommonSettings commonSettings) throws IOException {
    this.serviceProviderRegistry = serviceProviderRegistry;
    this.jobDao = jobDao;
    this.cryptoHelper = cryptoHelper;
    this.metrics = metrics;
    this.commonSettings = commonSettings;
    // TODO(rtannenbaum): This is our first metric we're recording, as an example. Refactor this
    // and all future metrics to a centralized place.
    createMetrics();
  }

  private void createMetrics() throws IOException {
    ImmutableSet<LabelDescriptor> labels = ImmutableSet.<LabelDescriptor>builder()
        .add(LabelDescriptor.create(TRANSFER_METRIC_SERVICE_MODE_LABEL, ValueType.STRING,
            "Service mode for this transfer"))
        .add(LabelDescriptor.create(TRANSFER_METRIC_SERVICE_LABEL, ValueType.STRING,
            "The service provider for this transfer")).build();
    metrics.createMetric(TRANSFER_METRIC_TYPE, "a data transfer to or from a service provider",
        Kind.CUMULATIVE, ValueType.INT64, labels);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Preconditions.checkArgument(
        PortabilityApiUtils.validateRequest(exchange, HttpMethods.GET, "/callback/.*"));
    logger.debug("received request: {}", exchange.getRequestURI());

    String redirect = handleExchange(exchange);
    logger.debug("redirecting to {}", redirect);
    exchange.getResponseHeaders().set(HEADER_LOCATION, redirect);
    exchange.sendResponseHeaders(303, -1);
  }

  private String handleExchange(HttpExchange exchange) throws IOException {
    String redirect = "/error";

    try {
      Headers requestHeaders = exchange.getRequestHeaders();

      String requestURL = PortabilityApiUtils
          .createURL(
              requestHeaders.getFirst(HEADER_HOST),
              exchange.getRequestURI().toString(),
              commonSettings.getEnv() != Environment.LOCAL);

      AuthorizationCodeResponseUrl authResponse = new AuthorizationCodeResponseUrl(requestURL);

      // check for user-denied error
      if (authResponse.getError() != null) {
        logger.warn("Authorization DENIED: {} Redirecting to /error", authResponse.getError());
        return redirect;
      }

      // retrieve cookie from exchange
      Map<String, HttpCookie> httpCookies = PortabilityApiUtils.getCookies(requestHeaders);
      HttpCookie encodedIdCookie = httpCookies.get(JsonKeys.ID_COOKIE_KEY);
      Preconditions
          .checkArgument(
              encodedIdCookie != null && !Strings.isNullOrEmpty(encodedIdCookie.getValue()),
              "Encoded Id cookie required");

      String jobId = JobUtils.decodeId(encodedIdCookie.getValue());
      String state = JobUtils.decodeId(authResponse.getState());

      // TODO: Remove sanity check
      Preconditions
          .checkState(state.equals(jobId), "Job id in cookie [%s] and request [%s] should match",
              jobId, state);

      PortabilityJob job;
      if (commonSettings.getEncryptedFlow()) {
        job = PortabilityApiUtils.lookupJobPendingAuthData(jobId, jobDao);
      } else {
        job = PortabilityApiUtils.lookupJob(jobId, jobDao);
      }
      PortableDataType dataType = JobUtils.getDataType(job.dataType());

      ServiceMode serviceMode = PortabilityApiUtils.getServiceMode(
          job,
          exchange.getRequestHeaders(),
          commonSettings.getEncryptedFlow());

      // TODO: Determine service from job or from authUrl path?
      String service =
          serviceMode == ServiceMode.EXPORT ? job.exportService() : job.importService();
      Preconditions.checkState(!Strings.isNullOrEmpty(service),
          "service not found, service: %s serviceMode: %s, jobId: %s", service, serviceMode, jobId);

      ImmutableMap<String, String> transferMetricLabels = new ImmutableMap.Builder<String, String>()
          .put(TRANSFER_METRIC_SERVICE_MODE_LABEL, serviceMode.toString())
          .put(TRANSFER_METRIC_SERVICE_LABEL, service.toString()).build();
      metrics.recordDataPoint(TRANSFER_METRIC_TYPE, ValueType.INT64, 1, transferMetricLabels);

      // Obtain the ServiceProvider from the registry
      OnlineAuthDataGenerator generator = serviceProviderRegistry.getOnlineAuth(service, dataType);

      // Retrieve initial auth data, if it existed
      AuthData initialAuthData = JobUtils.getInitialAuthData(job, serviceMode);

      // Generate and store auth data
      AuthData authData = generator
          .generateAuthData(PortabilityApiFlags.baseApiUrl(), authResponse.getCode(), jobId,
              initialAuthData, null);
      Preconditions.checkNotNull(authData, "Auth data should not be null");

      // The data will be passed thru to the server via the cookie.
      if (!commonSettings.getEncryptedFlow()) {
        // Update the job
        PortabilityJob updatedJob = JobUtils.setAuthData(job, authData, serviceMode);
        jobDao.updateJob(updatedJob);
      } else {
        // Set new cookie
        cryptoHelper
            .encryptAndSetCookie(exchange.getResponseHeaders(), job.id(), serviceMode, authData);
      }

      redirect =
          PortabilityApiFlags.baseUrl() + ((serviceMode == ServiceMode.EXPORT) ? "/next" : "/copy");
    } catch (Exception e) {
      logger.error("Error handling request: {}", e);
      throw e;
    }

    return redirect;
  }
}
