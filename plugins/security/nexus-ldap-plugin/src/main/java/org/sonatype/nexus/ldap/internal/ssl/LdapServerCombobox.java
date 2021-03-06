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
package org.sonatype.nexus.ldap.internal.ssl;

import org.sonatype.nexus.formfields.ComboboxFormField;
import org.sonatype.sisu.goodies.i18n.I18N;
import org.sonatype.sisu.goodies.i18n.MessageBundle;

public class LdapServerCombobox
    extends ComboboxFormField<String>
{

  private static interface Messages
      extends MessageBundle
  {

    @DefaultMessage("LDAP Server")
    String label();

    @DefaultMessage("Select an LDAP server.")
    String helpText();

  }

  private static final Messages messages = I18N.create(Messages.class);

  public LdapServerCombobox(final String id, final String label, final String helpText, final boolean required) {
    super(id, label, helpText, required);
  }

  public LdapServerCombobox(final String id, final boolean required) {
    super(id, messages.label(), messages.helpText(), required);
  }

  public LdapServerCombobox(final String id) {
    super(id, messages.label(), messages.helpText(), false);
  }

  /**
   * @since 3.0
   */
  @Override
  public String getStoreApi() {
    return "ldap_LdapServer.readReferences";
  }

}
