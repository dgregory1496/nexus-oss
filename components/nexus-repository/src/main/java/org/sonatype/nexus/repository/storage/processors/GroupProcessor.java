package org.sonatype.nexus.repository.storage.processors;

import java.util.List;

import org.sonatype.nexus.repository.storage.ProcessorContext;
import org.sonatype.nexus.repository.storage.Processor;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;

import com.google.common.collect.Lists;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;

/**
 * Component group iterating processor.
 *
 * @since 3.0
 */
public class GroupProcessor
    extends Processor
{
  @Override
  public void process(final ProcessorContext context) {
    final List<String> groups = groups(context);
    for (String group : groups) {
      context.getAttributes().set(StorageFacet.P_GROUP, group);
      context.proceed();
    }
  }

  private List<String> groups(final ProcessorContext context) {
    final List<String> groups = Lists.newArrayList();
    try (StorageTx tx = context.getStorageTxSupplier().get()) {
      final Iterable<ODocument> docs = tx.getDb()
          .command(new OCommandSQL("select distinct(group) as val from component limit -1"))
          .execute();
      for (ODocument doc : docs) {
        final String docGroup = doc.field("val", OType.STRING);
        groups.add(docGroup);
      }

    }
    return groups;
  }
}
