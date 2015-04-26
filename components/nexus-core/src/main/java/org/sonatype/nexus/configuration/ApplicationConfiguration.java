/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.configuration;

import java.io.File;
import java.io.IOException;

import org.sonatype.nexus.ApplicationDirectories;
import org.sonatype.nexus.configuration.model.Configuration;
import org.sonatype.nexus.proxy.storage.local.LocalStorageContext;
import org.sonatype.nexus.proxy.storage.remote.RemoteStorageContext;

/**
 * ApplicationConfiguration is the main component to have and maintain configuration.
 */
@Deprecated
public interface ApplicationConfiguration
{
  /**
   * Gets the working directory as file. The directory is created if needed and is guaranteed to exists.
   *
   * @deprecated Use {@link ApplicationDirectories}
   */
  @Deprecated
  File getWorkingDirectory();

  /**
   * Gets the working directory with some subpath. The directory is created and is guaranteed to exists.
   *
   * @deprecated Use {@link ApplicationDirectories}
   */
  @Deprecated
  File getWorkingDirectory(String key);

  /**
   * Returns the configuration directory. It defaults to $NEXUS_WORK/etc.
   */
  File getConfigurationDirectory();

  /**
   * Gets the top level local storage context.
   */
  LocalStorageContext getGlobalLocalStorageContext();

  /**
   * Gets the top level remote storage context.
   *
   * @deprecated Use {@code @Named("global") Provider<RemoteStorageContext>} instead.
   */
  @Deprecated
  RemoteStorageContext getGlobalRemoteStorageContext();

  /**
   * Saves the configuration.
   */
  void saveConfiguration() throws IOException;

  /**
   * Gets the Configuration object.
   *
   * @deprecated you should use setters/getters directly on Configurable instances, and not tampering with
   *             Configuration model directly!
   */
  @Deprecated
  Configuration getConfigurationModel();

  // FIXME: Only used by tests
  void loadConfiguration() throws IOException;

  /**
   * Explicit loading of configuration. Enables to force reloading of config.
   */
  void loadConfiguration(boolean forceReload) throws IOException;
}
