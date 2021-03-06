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

import org.sonatype.nexus.NexusAppTestSupport;
import org.sonatype.nexus.proxy.repository.DefaultRemoteHttpProxySettings;
import org.sonatype.sisu.goodies.eventbus.EventBus;

import com.google.common.eventbus.Subscribe;
import org.junit.Assert;
import org.junit.Test;

/**
 * @since 2.6
 */
public class DefaultGlobalRemoteProxySettingsTest
    extends NexusAppTestSupport
{

  @SuppressWarnings("unchecked")
  @Test
  public void testEvents()
      throws Exception
  {
    ApplicationConfiguration cfg = lookup(ApplicationConfiguration.class);
    cfg.loadConfiguration();

    final GlobalRemoteProxySettingsChangedEvent[] event = new GlobalRemoteProxySettingsChangedEvent[1];
    lookup(EventBus.class).register(new Object()
    {
      @Subscribe
      public void onEvent(GlobalRemoteProxySettingsChangedEvent evt) {
        event[0] = evt;
      }
    });

    GlobalRemoteProxySettings settings = lookup(GlobalRemoteProxySettings.class);

    final DefaultRemoteHttpProxySettings httpProxySettings = new DefaultRemoteHttpProxySettings();
    httpProxySettings.setHostname("foo.bar.com");
    httpProxySettings.setPort(1234);

    settings.setHttpProxySettings(httpProxySettings);

    cfg.saveConfiguration();

    Assert.assertNotNull(event[0]);
    Assert.assertEquals(settings, event[0].getSettings());
    Assert.assertEquals("foo.bar.com", event[0].getSettings().getHttpProxySettings().getHostname());
    Assert.assertEquals(1234, event[0].getSettings().getHttpProxySettings().getPort());
  }

}
