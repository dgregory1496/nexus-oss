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
package org.sonatype.nexus.repository.maven.internal.maven2.metadata;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.maven.internal.MavenFacet;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.processors.ComponentGroupNames;
import org.sonatype.nexus.repository.storage.processors.ComponentGroups;
import org.sonatype.nexus.repository.storage.processors.Loop;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Just a testing class, here while men at work, to be removed soon.
 */
public class Tester
{
  private final Repository repository;

  public Tester(final Repository repository) {
    this.repository = repository;
  }

  public void rebuildMetadata() {
    final StorageFacet storageFacet = repository.facet(StorageFacet.class);
    final MavenFacet mavenFacet = repository.facet(MavenFacet.class);

    final MetadataBuilder metadataBuilder = new MetadataBuilder();
    final MetadataUpdater metadataUpdater = new MetadataUpdater(mavenFacet);

    storageFacet.process(
        null, /* initial Ctx */
        // TODO: narrow loops to given repository
        new Loop<>(new ComponentGroups()), // -- loop distinct(group)
        new GProcessor(metadataBuilder, metadataUpdater), // around groups
        // TODO: narrow loops to given repository
        new Loop<>(new ComponentGroupNames()), // -- loop distinct(name)
        new AProcessor(metadataBuilder, metadataUpdater), // around names
        // TODO: narrow loops to given repository
        new Loop<>(new ComponentGroupNameBaseVersions()), // -- loop distinct(attributes.maven2.baseVersion)
        new BVProcessor(metadataBuilder, metadataUpdater), // around baseVersion
        new MetadataProcessor(metadataBuilder, mavenFacet.getMavenPathParser()) // comp+asset
    );
  }
}
