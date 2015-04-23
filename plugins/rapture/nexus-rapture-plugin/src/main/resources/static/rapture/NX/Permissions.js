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
/*global Ext*/

/**
 * Permissions helper.
 *
 * @since 3.0
 */
Ext.define('NX.Permissions', {
  singleton: true,

  /**
   * @private
   * Map between permissions id and value.
   */
  permissions: undefined,

  /**
   * @public
   * @returns {boolean} If permissions had been set (loaded from server)
   */
  available: function() {
    var me = this;
    return Ext.isDefined(me.permissions);
  },

  /**
   * @public
   */
  setPermissions: function(permissions) {
    var me = this,
        perms = permissions;

    if (Ext.isArray(permissions)) {
      perms = {};
      Ext.each(permissions, function(entry) {
        if (entry.id && entry.value) {
          perms[entry.id] = entry.value;
        }
      });
    }

    me.permissions = Ext.apply({}, perms);
  },

  resetPermissions: function() {
    delete this.permissions;
  },

  /**
   * @public
   */
  check: function(value, perm) {
    var me = this;

    if (!me.available()) {
      return false;
    }

    return me.permissions[value + ':' + perm] === true;
  },

  /**
   * @public
   */
  checkAny: function(perm) {
    var me = this,
        hasAny = false;

    if (!me.available()) {
      return false;
    }

    if (me.permissions['nexus:*'] === true) {
      return true;
    }

    Ext.Object.each(me.permissions, function(key, value) {
      if (Ext.String.startsWith(key, perm) && value === true) {
        hasAny = true;
        return false;
      }
      return true;
    });

    return hasAny;
  }

});
