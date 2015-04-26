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
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.sonatype.nexus.ApplicationDirectories;
import org.sonatype.nexus.common.throwables.ConfigurationException;
import org.sonatype.nexus.configuration.model.Configuration;
import org.sonatype.nexus.configuration.source.ApplicationConfigurationSource;
import org.sonatype.nexus.configuration.validator.ApplicationConfigurationValidator;
import org.sonatype.nexus.events.AbstractVetoableEvent;
import org.sonatype.nexus.events.Veto;
import org.sonatype.nexus.jmx.reflect.ManagedAttribute;
import org.sonatype.nexus.jmx.reflect.ManagedObject;
import org.sonatype.nexus.jmx.reflect.ManagedOperation;
import org.sonatype.nexus.proxy.cache.CacheManager;
import org.sonatype.nexus.proxy.storage.local.DefaultLocalStorageContext;
import org.sonatype.nexus.proxy.storage.local.LocalStorageContext;
import org.sonatype.nexus.proxy.storage.remote.DefaultRemoteStorageContext;
import org.sonatype.nexus.proxy.storage.remote.RemoteStorageContext;
import org.sonatype.nexus.validation.ValidationResponse;
import org.sonatype.sisu.goodies.common.ComponentSupport;
import org.sonatype.sisu.goodies.eventbus.EventBus;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.codehaus.plexus.util.ExceptionUtils;
import org.eclipse.sisu.inject.BeanLocator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class DefaultNexusConfiguration is responsible for config management. It actually keeps in sync Nexus internal
 * state with persisted user configuration. All changes incoming through its iface is reflect/maintained in Nexus
 * current state and Nexus user config.
 *
 * @author cstamas
 */
@Singleton
@Named
@ManagedObject(
    typeClass = ApplicationConfiguration.class,
    description = "Application configuration"
)
public class DefaultApplicationConfiguration
    extends ComponentSupport
    implements ApplicationConfiguration
{
  /**
   * Only to have UTs work
   */
  private final CacheManager cacheManager;

  private final BeanLocator beanLocator;

  private final EventBus eventBus;

  private final ApplicationConfigurationSource configurationSource;

  private final Provider<GlobalRemoteConnectionSettings> globalRemoteConnectionSettingsProvider;

  private final Provider<GlobalRemoteProxySettings> globalRemoteProxySettingsProvider;

  private final ApplicationConfigurationValidator configurationValidator;

  private final ClassLoader uberClassLoader;

  private final ApplicationDirectories applicationDirectories;

  /**
   * The global local storage context.
   */
  private DefaultLocalStorageContext globalLocalStorageContext;

  /**
   * The global remote storage context.
   */
  private DefaultRemoteStorageContext globalRemoteStorageContext;

  /**
   * The config dir
   */
  private File configurationDirectory;

  @Inject
  public DefaultApplicationConfiguration(final CacheManager cacheManager,
                                         final BeanLocator beanLocator,
                                         final EventBus eventBus,
                                         final @Named("file") ApplicationConfigurationSource configurationSource,
                                         final Provider<GlobalRemoteConnectionSettings> globalRemoteConnectionSettingsProvider,
                                         final Provider<GlobalRemoteProxySettings> globalRemoteProxySettingsProvider,
                                         final ApplicationConfigurationValidator configurationValidator,
                                         final @Named("nexus-uber") ClassLoader uberClassLoader,
                                         final ApplicationDirectories applicationDirectories)
  {
    this.cacheManager = checkNotNull(cacheManager);
    this.beanLocator = checkNotNull(beanLocator);
    this.eventBus = checkNotNull(eventBus);
    this.configurationSource = checkNotNull(configurationSource);
    this.globalRemoteConnectionSettingsProvider = checkNotNull(globalRemoteConnectionSettingsProvider);
    this.globalRemoteProxySettingsProvider = checkNotNull(globalRemoteProxySettingsProvider);
    this.configurationValidator = checkNotNull(configurationValidator);
    this.uberClassLoader = checkNotNull(uberClassLoader);
    this.applicationDirectories = checkNotNull(applicationDirectories);
    this.configurationDirectory = applicationDirectories.getWorkDirectory("etc");
  }

  @Override
  public void loadConfiguration() throws IOException {
    loadConfiguration(false);
  }

  private static class VetoFormatterRequest
  {
    private AbstractVetoableEvent<?> event;

    private boolean detailed;

    public VetoFormatterRequest(AbstractVetoableEvent<?> event, boolean detailed) {
      this.event = event;
      this.detailed = detailed;
    }

    public AbstractVetoableEvent<?> getEvent() {
      return event;
    }

    public boolean isDetailed() {
      return detailed;
    }
  }

  private static class DefaultVetoFormatter
  {
    private static final String LINE_SEPERATOR = System.getProperty("line.separator");

    public String format(VetoFormatterRequest request) {
      StringBuilder sb = new StringBuilder();

      if (request != null
          && request.getEvent() != null
          && request.getEvent().isVetoed()) {
        sb.append("Event ").append(request.getEvent().toString()).append(" has been vetoed by one or more components.");

        if (request.isDetailed()) {
          sb.append(LINE_SEPERATOR);

          for (Veto veto : request.getEvent().getVetos()) {
            sb.append("vetoer: ").append(veto.getVetoer().toString());
            sb.append("cause:");
            sb.append(LINE_SEPERATOR);
            sb.append(ExceptionUtils.getFullStackTrace(veto.getReason()));
            sb.append(LINE_SEPERATOR);
          }
        }
      }

      return sb.toString();
    }
  }

  @Override
  @ManagedOperation
  public synchronized void loadConfiguration(boolean force) throws IOException {
    if (force || configurationSource.getConfiguration() == null) {
      log.info("Loading Nexus Configuration...");

      configurationSource.loadConfiguration();

      globalLocalStorageContext = new DefaultLocalStorageContext(null);

      // create global remote ctx
      // this one has no parent
      globalRemoteStorageContext = new DefaultRemoteStorageContext(null);

      final GlobalRemoteConnectionSettings globalRemoteConnectionSettings =
          globalRemoteConnectionSettingsProvider.get();

      // TODO: hack
      ((DefaultGlobalRemoteConnectionSettings) globalRemoteConnectionSettings).configure(this);
      globalRemoteStorageContext.setRemoteConnectionSettings(globalRemoteConnectionSettings);

      final GlobalRemoteProxySettings globalRemoteProxySettings = globalRemoteProxySettingsProvider.get();

      // TODO: hack
      ((DefaultGlobalRemoteProxySettings) globalRemoteProxySettings).configure(this);
      globalRemoteStorageContext.setRemoteProxySettings(globalRemoteProxySettings);

      ConfigurationPrepareForLoadEvent loadEvent = new ConfigurationPrepareForLoadEvent(this);

      eventBus.post(loadEvent);

      if (loadEvent.isVetoed()) {
        log.info(new DefaultVetoFormatter().format(new VetoFormatterRequest(loadEvent, log.isDebugEnabled())));

        throw new ConfigurationException("The Nexus configuration is invalid!");
      }

      applyConfiguration();

      // we successfully loaded config
      eventBus.post(new ConfigurationLoadEvent(this));
    }
  }

  private String changesToString(final Collection<Configurable> changes) {
    final StringBuilder sb = new StringBuilder();

    if (changes != null) {
      sb.append(Collections2.transform(changes, new Function<Configurable, String>()
      {
        @Override
        public String apply(final Configurable input) {
          return input.getName();
        }
      }));
    }

    return sb.toString();
  }

  private void logApplyConfiguration(final Collection<Configurable> changes) {
    final String userId = getCurrentUserId();

    if (changes != null && changes.size() > 0) {
      if (Strings.isNullOrEmpty(userId)) {
        // should not really happen, we should always have subject (at least anon), but...
        log.info("Applying Nexus Configuration due to changes in {}...", changesToString(changes));
      }
      else {
        // usually what happens on config change
        log.info("Applying Nexus Configuration due to changes in {} made by {}...",
            changesToString(changes), userId);
      }
    }
    else {
      if (Strings.isNullOrEmpty(userId)) {
        // usually on boot: no changes since "all" changed, and no subject either
        log.info("Applying Nexus Configuration...");
      }
      else {
        // inperfection of config framework, ie. on adding new component to config system (new repo)
        log.info("Applying Nexus Configuration made by {}...", userId);
      }
    }
  }

  /**
   * Returns the userId ("main principal" in Shiro lingo) of the user that is the principal of currently executing
   * activity (like configuration save) for logging purposes only. It uses Shiro API to get the information, and will
   * return the String userId, or {@code null} if it's impossible to determine it, as current thread (the one
   * invoking
   * this method) does not have bound Subject. If more information needed about current user, Shiro and/or Security
   * API of Nexus should be used, this method is not a definitive source of users in Nexus Security.
   */
  private String getCurrentUserId() {
    try {
      final Subject subject = SecurityUtils.getSubject();
      if (subject != null && subject.getPrincipal() != null) {
        return subject.getPrincipal().toString();
      }
    }
    catch (final Exception e) {
      // NEXUS-5749: Prevent interruption of configuration save (and hence, data loss) for any
      // exception thrown while gathering userId for logging purposes.
      log.warn("Could not obtain Shiro subject:", e);
    }
    return null;
  }

  public synchronized boolean applyConfiguration() {
    log.debug("Applying Nexus Configuration...");

    ConfigurationPrepareForSaveEvent prepare = new ConfigurationPrepareForSaveEvent(this);

    eventBus.post(prepare);

    if (!prepare.isVetoed()) {
      logApplyConfiguration(prepare.getChanges());

      eventBus.post(new ConfigurationCommitEvent(this));

      eventBus.post(new ConfigurationChangeEvent(this, prepare.getChanges(), getCurrentUserId()));

      return true;
    }
    else {
      log.info(new DefaultVetoFormatter().format(new VetoFormatterRequest(prepare, log.isDebugEnabled())));

      eventBus.post(new ConfigurationRollbackEvent(this));

      return false;
    }
  }

  @Override
  @ManagedOperation
  public synchronized void saveConfiguration() throws IOException {
    if (applyConfiguration()) {
      // validate before we do anything
      ValidationResponse response = configurationValidator.validateModel(configurationSource.getConfiguration());
      if (!response.isValid()) {
        this.log.error("Saving nexus configuration caused unexpected error:\n" + response.toString());
        throw new IOException("Saving nexus configuration caused unexpected error:\n" + response.toString());
      }
      // END <<<

      configurationSource.storeConfiguration();

      // we successfully saved config
      eventBus.post(new ConfigurationSaveEvent(this));
    }
  }

  @Override
  @Deprecated
  public Configuration getConfigurationModel() {
    return configurationSource.getConfiguration();
  }

  @Override
  public LocalStorageContext getGlobalLocalStorageContext() {
    return globalLocalStorageContext;
  }

  @Override
  public RemoteStorageContext getGlobalRemoteStorageContext() {
    return globalRemoteStorageContext;
  }

  @Override
  @Deprecated
  @ManagedAttribute
  public File getWorkingDirectory() {
    return applicationDirectories.getWorkDirectory();
  }

  @Override
  @Deprecated
  public File getWorkingDirectory(String key) {
    return applicationDirectories.getWorkDirectory(key);
  }

  @Override
  @ManagedAttribute
  public File getConfigurationDirectory() {
    return configurationDirectory;
  }
}
