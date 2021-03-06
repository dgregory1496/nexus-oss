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
package com.sonatype.nexus.repository.nuget.internal;

import java.util.HashMap;

import com.sonatype.nexus.repository.nuget.internal.odata.ODataTemplates;

import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import com.google.common.collect.Maps;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class NugetGalleryFacetEntryTest
    extends TestSupport
{
  @Test
  public void packageEntrySmokeTest() throws Exception {
    final String packageId = "screwdriver";
    final String version = "0.1.1";

    final NugetGalleryFacetImpl galleryFacet = spy(new NugetGalleryFacetImpl());

    final StorageTx tx = mock(StorageTx.class);
    doReturn(tx).when(galleryFacet).openStorageTx();

    final Component component = mock(Component.class);

    // Wire the mocks together: component has asset, asset has blobRef
    doReturn(component).when(galleryFacet).findComponent(tx, packageId, version);
    doReturn(mock(NestedAttributesMap.class)).when(component).formatAttributes();

    final HashMap<String, ?> data = Maps.newHashMap();
    doReturn(data).when(galleryFacet).toData(any(NestedAttributesMap.class),
        anyMapOf(String.class, String.class));

    galleryFacet.entry("base", packageId, version);

    verify(galleryFacet).interpolateTemplate(ODataTemplates.NUGET_ENTRY, data);
  }
}
