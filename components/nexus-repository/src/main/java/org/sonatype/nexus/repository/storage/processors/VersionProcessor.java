package org.sonatype.nexus.repository.storage.processors;

import java.util.List;

import org.sonatype.nexus.repository.storage.Processor;
import org.sonatype.nexus.repository.storage.ProcessorContext;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;

import com.google.common.collect.Lists;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;

/**
 * Component version iterating processor, requires {@link StorageFacet#P_GROUP} and {@link StorageFacet#P_NAME}
 * attributes set in context.
 *
 * @since 3.0
 */
public class VersionProcessor
    extends Processor
{
  @Override
  public void process(final ProcessorContext context) {
    List<String> versions = versions(context);
    for (String version : versions) {
      context.getAttributes().set(StorageFacet.P_VERSION, version);
      context.proceed();
    }
  }

  private List<String> versions(final ProcessorContext context) {
    final String group = context.getAttributes().require(StorageFacet.P_GROUP, String.class);
    final String name = context.getAttributes().require(StorageFacet.P_NAME, String.class);
    final List<String> versions = Lists.newArrayList();
    try (StorageTx tx = context.getStorageTxSupplier().get()) {
      Iterable<ODocument> docs = tx.getDb()
          .command(new OCommandSQL("select distinct(version) as val from component where group=? and name=? limit -1"))
          .execute(group, name);
      for (ODocument doc : docs) {
        final String docVersion = doc.field("val", OType.STRING);
        versions.add(docVersion);
      }

    }
    return versions;
  }
}
