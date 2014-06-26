/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2014 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
/**
 * Storage file info controller.
 *
 * @since 3.0
 */
Ext.define('NX.coreui.controller.StorageFileInfo', {
  extend: 'Ext.app.Controller',

  views: [
    'repositorybrowse.StorageFileInfo'
  ],

  /**
   * @override
   */
  init: function() {
    var me = this;

    me.listen({
      component: {
        'nx-coreui-repositorybrowse-storagefilecontainer': {
          updated: me.onUpdated
        }
      }
    });
  },

  onUpdated: function(detailPanel, repositoryId, path) {
    var me = this,
        panel = detailPanel.down('nx-coreui-repositorybrowse-storagefileinfo');

    if (!panel) {
      panel = detailPanel.add({ xtype: 'nx-coreui-repositorybrowse-storagefileinfo' });
    }

    NX.direct.coreui_RepositoryStorage.readInfo(repositoryId, path, function(response) {
      var info = {};
      if (Ext.isDefined(response) && response.success && response.data) {
        info = {
          'Path': response.data['path'],
          'Size': me.toSizeString(response.data['size']),
          'Uploaded by': response.data['createdBy'],
          'Uploaded Date': Ext.Date.parse(response.data['created'], 'c'),
          'Last Modified': Ext.Date.parse(response.data['modified'], 'c'),
          'SHA1': response.data['sha1'],
          'MD5': response.data['md5']
        };
      }
      panel.showInfo(info);
    });
  },

  toSizeString: function(v) {
    if (typeof v !== 'number') {
      return '<unknown>';
    }
    if (v < 0) {
      return '0 Bytes';
    }
    if (v < 1024) {
      return v + ' Bytes';
    }
    if (v < 1048576) {
      return (v / 1024).toFixed(2) + ' KB';
    }
    if (v < 1073741824) {
      return (v / 1048576).toFixed(2) + ' MB';
    }
    return (v / 1073741824).toFixed(2) + ' GB';
  }

});