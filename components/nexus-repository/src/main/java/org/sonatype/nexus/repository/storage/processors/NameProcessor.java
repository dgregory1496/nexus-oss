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
 * Component name iterating processor, requires {@link StorageFacet#P_GROUP} attribute set in context.
 *
 * @since 3.0
 */
public class NameProcessor
    extends Processor
{
  @Override
  public void process(final ProcessorContext context) {
    List<String> names = names(context);
    for (String name : names) {
      context.getAttributes().set(StorageFacet.P_NAME, name);
      context.proceed();
    }
  }

  private List<String> names(final ProcessorContext context) {
    final String group = context.getAttributes().require(StorageFacet.P_GROUP, String.class);
    final List<String> names = Lists.newArrayList();
    try (StorageTx tx = context.getStorageTxSupplier().get()) {
      Iterable<ODocument> docs = tx.getDb()
          .command(new OCommandSQL("select distinct(name) as val from component where group=? limit -1"))
          .execute(group);
      for (ODocument doc : docs) {
        final String docName = doc.field("val", OType.STRING);
        names.add(docName);
      }

    }
    return names;
  }
}
