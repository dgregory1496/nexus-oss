package org.sonatype.nexus.repository.storage;

/**
 * Basic empty processor.
 *
 * @since 3.0
 */
public abstract class Processor
{
g  public void process(final ProcessorContext context) {
    context.proceed();
  }
}
