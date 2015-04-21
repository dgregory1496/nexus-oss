package org.sonatype.nexus.repository.storage.processors;

import java.util.List;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.Processor;
import org.sonatype.nexus.repository.storage.ProcessorContext;
import org.sonatype.nexus.repository.storage.StorageTx;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Looping processor.
 *
 * @since 3.0
 */
public class Loop<T>
    extends Processor
{
  private final ElementSource<T> elementSource;

  public Loop(final ElementSource<T> elementSource) {
    this.elementSource = checkNotNull(elementSource);
  }

  @Override
  public void process(final ProcessorContext context) {
    final List<T> elements = elements(context);
    for (T element : elements) {
      elementSource.addToContext(context, element);
      context.proceed();
      elementSource.removeFromContext(context, element);
    }
  }

  private List<T> elements(final ProcessorContext context) {
    final List<T> elements = Lists.newArrayList();
    Iterables.addAll(
        elements,
        elementSource.elements(context)
    );
    return elements;
  }
}
