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
package org.dataportabilityproject.job;

import com.google.common.base.Converter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.dataportabilityproject.shared.auth.AuthData;

/**
 * Converter from {@link PortabilityJob} to and from a Map suitable for storing in a key value
 * storage layer.
 */
final class PortabilityJobConverter extends Converter<PortabilityJob, Map<String, Object>> {
  // Keys for specific values in the key value store
  private static final String ID_DATA_KEY = "UUID";
  private static final String DATA_TYPE_DATA_KEY = "DATA_TYPE";
  private static final String EXPORT_SERVICE_DATA_KEY = "EXPORT_SERVICE";
  private static final String EXPORT_ACCOUNT_DATA_KEY = "EXPORT_ACCOUNT";
  private static final String EXPORT_INITIAL_AUTH_DATA_KEY = "EXPORT_INITIAL_AUTH_DATA";
  @Deprecated private static final String EXPORT_AUTH_DATA_KEY = "EXPORT_AUTH_DATA";
  private static final String EXPORT_ENCRYPTED_AUTH_DATA_KEY = "EXPORT_ENCRYPTED_AUTH_DATA_KEY";
  private static final String IMPORT_SERVICE_DATA_KEY = "IMPORT_SERVICE";
  private static final String IMPORT_ACCOUNT_DATA_KEY = "IMPORT_ACCOUNT";
  private static final String IMPORT_INITIAL_AUTH_DATA_KEY = "IMPORT_INITIAL_AUTH_DATA";
  @Deprecated private static final String IMPORT_AUTH_DATA_KEY = "IMPORT_AUTH_DATA";
  private static final String IMPORT_ENCRYPTED_AUTH_DATA_KEY = "IMPORT_ENCRYPTED_AUTH_DATA";
  private static final String SESSION_KEY = "SESSION_KEY";
  private static final String WORKER_INSTANCE_PUBLIC_KEY = "WORKER_INSTANCE_PUBLIC_KEY";
  private static final String WORKER_INSTANCE_PRIVATE_KEY = "WORKER_INSTANCE_PRIVATE_KEY";

  /**
   * Converts a {@link PortabilityJob} to a map of key value pairs.
   */
  @Override
  protected Map<String, Object> doForward(PortabilityJob job) {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

    // Id is the key so it is required
    Preconditions.checkArgument(!Strings.isNullOrEmpty(job.id()), "Invalid uuid");
    builder.put(ID_DATA_KEY, job.id());

    // Data type may not be set during initial flow
    if(!Strings.isNullOrEmpty(job.dataType())) {
      builder.put(DATA_TYPE_DATA_KEY, job.dataType());
    }
    // Validate and add export service information
    if(!Strings.isNullOrEmpty(job.exportService())){
      builder.put(EXPORT_SERVICE_DATA_KEY, job.exportService());
    }
    if(!Strings.isNullOrEmpty(job.exportAccount())) {
      builder.put(EXPORT_ACCOUNT_DATA_KEY, job.exportAccount());
    }
    if (null != job.exportInitialAuthData()) {
      builder.put(EXPORT_INITIAL_AUTH_DATA_KEY, job.exportInitialAuthData());
    }
    // TODO: remove in encryptedflow
    if (null != job.exportAuthData()) {
      builder.put(EXPORT_AUTH_DATA_KEY, job.exportAuthData());
    }
    if (null != job.encryptedExportAuthData()) {
      builder.put(EXPORT_ENCRYPTED_AUTH_DATA_KEY, job.encryptedExportAuthData());
    }
    // Validate and add import service information
    if(!Strings.isNullOrEmpty(job.importService())){
      builder.put(IMPORT_SERVICE_DATA_KEY, job.importService());
    }
    if(!Strings.isNullOrEmpty(job.importAccount())) {
      builder.put(IMPORT_ACCOUNT_DATA_KEY, job.importAccount());
    }
    if (null != job.importInitialAuthData()) {
      builder.put(IMPORT_INITIAL_AUTH_DATA_KEY, job.importInitialAuthData());
    }
    // TODO: remove in encryptedflow
    if (null != job.importAuthData()) {
      builder.put(IMPORT_AUTH_DATA_KEY, job.importAuthData());
    }
    if (null != job.encryptedImportAuthData()) {
      builder.put(IMPORT_ENCRYPTED_AUTH_DATA_KEY, job.encryptedImportAuthData());
    }
    if (null != job.sessionKey()) {
      builder.put(SESSION_KEY, job.sessionKey());
    }
    if (null != job.workerInstancePublicKey()) {
      builder.put(WORKER_INSTANCE_PUBLIC_KEY, job.workerInstancePublicKey());
    }
    if (null != job.workerInstancePrivateKey()) {
      builder.put(WORKER_INSTANCE_PRIVATE_KEY, job.workerInstancePrivateKey());
    }
    return builder.build();
  }

  /**
   * Converts a Map of key value pairs to a {@link PortabilityJob}.
   */
  @Override
  protected PortabilityJob doBackward(Map<String, Object> data) {
    Preconditions.checkArgument(!isStringValueNullOrEmpty(data, ID_DATA_KEY), "uuid missing");
    // Add required data
    PortabilityJob.Builder builder = PortabilityJob.builder();
    builder.setId(getString(data, ID_DATA_KEY));

    // newly created sessions will not contain any data type selection
    String dataType = getString(data, DATA_TYPE_DATA_KEY);
    if (dataType == null) {
      return builder.build();
    }
    // Return selected data type
    builder.setDataType(dataType);

    // Return export information if exists
    if (!isStringValueNullOrEmpty(data, EXPORT_SERVICE_DATA_KEY)) {
      builder.setExportService(getString(data, EXPORT_SERVICE_DATA_KEY));
    }
    if(!isStringValueNullOrEmpty(data, EXPORT_ACCOUNT_DATA_KEY)) {
      builder.setExportAccount(getString(data, EXPORT_ACCOUNT_DATA_KEY));
    }
    if (data.get(EXPORT_INITIAL_AUTH_DATA_KEY) != null) {
      builder.setExportInitialAuthData((AuthData) data.get(EXPORT_INITIAL_AUTH_DATA_KEY));
    }
    if (data.get(EXPORT_AUTH_DATA_KEY) != null) {
      builder.setExportAuthData((AuthData) data.get(EXPORT_AUTH_DATA_KEY));
    }
    if (data.get(EXPORT_ENCRYPTED_AUTH_DATA_KEY) != null) {
      builder.setEncryptedExportAuthData(getString(data, EXPORT_ENCRYPTED_AUTH_DATA_KEY));
    }
    if (!isStringValueNullOrEmpty(data, IMPORT_SERVICE_DATA_KEY)) {
      builder.setImportService(getString(data, IMPORT_SERVICE_DATA_KEY));
    }
    if (!isStringValueNullOrEmpty(data, IMPORT_ACCOUNT_DATA_KEY)) {
      builder.setImportAccount(getString(data, IMPORT_ACCOUNT_DATA_KEY));
    }
    if (data.get(IMPORT_INITIAL_AUTH_DATA_KEY) != null) {
      builder.setImportInitialAuthData((AuthData) data.get(IMPORT_INITIAL_AUTH_DATA_KEY));
    }
    if (data.get(IMPORT_AUTH_DATA_KEY) != null) {
      builder.setImportAuthData((AuthData) data.get(IMPORT_AUTH_DATA_KEY));
    }
    if (data.get(IMPORT_ENCRYPTED_AUTH_DATA_KEY) != null) {
      builder.setEncryptedImportAuthData(getString(data, IMPORT_ENCRYPTED_AUTH_DATA_KEY));
    }
    if (data.get(SESSION_KEY) != null) {
      builder.setSessionKey(getString(data, SESSION_KEY));
    }
    if (data.get(WORKER_INSTANCE_PUBLIC_KEY) != null) {
      builder.setWorkerInstancePublicKey(getString(data, WORKER_INSTANCE_PUBLIC_KEY));
    }
    if (data.get(WORKER_INSTANCE_PRIVATE_KEY) != null) {
      builder.setWorkerInstancePrivateKey(getString(data, WORKER_INSTANCE_PRIVATE_KEY));
    }
    return builder.build();
  }

  private static boolean isStringValueNullOrEmpty(Map<String, Object> map, String key) {
    return map.containsKey(key) && Strings.isNullOrEmpty(getString(map, key));
  }

  private static String getString(Map<String, Object> map, String key) {
    return (String) map.get(key);
  }
}
