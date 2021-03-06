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
/*global Ext, NX*/

/**
 * Upload artifact form.
 *
 * @since 3.0
 */
Ext.define('NX.coreui.view.upload.UploadArtifact', {
  extend: 'Ext.form.Panel',
  alias: 'widget.nx-coreui-upload-artifact',
  ui: 'nx-subsection',
  frame: true,
  requires: [
    'NX.I18n'
  ],

  defaults: {
    xtype: 'textfield',
    allowBlank: false
  },

  items: [
    {
      xtype: 'label',
      html: '<p>' + NX.I18n.get('BROWSE_MAVEN_ARTIFACT_HELP') + '</p>'
    },
    { xtype: 'nx-coreui-upload-artifact-coordinates', hidden: true, disabled: true }
  ],

  buttonAlign: 'left',
  buttons: [
    { text: NX.I18n.get('BROWSE_MAVEN_ARTIFACT_UPLOAD_BUTTON'), action: 'upload', ui: 'nx-primary', formBind: true },
    { text: NX.I18n.get('BROWSE_MAVEN_ARTIFACT_DISCARD_BUTTON'), action: 'discard' }
  ]

});
