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
package org.sonatype.nexus.coreui

import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.validation.Valid
import javax.validation.constraints.NotNull
import javax.validation.groups.Default

import org.sonatype.nexus.extdirect.DirectComponent
import org.sonatype.nexus.extdirect.DirectComponentSupport
import org.sonatype.nexus.extdirect.model.StoreLoadParameters
import org.sonatype.nexus.repository.MissingFacetException
import org.sonatype.nexus.repository.Recipe
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.group.GroupFacet
import org.sonatype.nexus.repository.httpclient.HttpClientFacet
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.validation.Validate
import org.sonatype.nexus.validation.group.Create
import org.sonatype.nexus.validation.group.Update
import org.sonatype.nexus.web.BaseUrlHolder

import com.softwarementors.extjs.djn.config.annotations.DirectAction
import com.softwarementors.extjs.djn.config.annotations.DirectMethod
import com.softwarementors.extjs.djn.config.annotations.DirectPollMethod
import groovy.transform.PackageScope
import org.apache.shiro.authz.annotation.RequiresAuthentication
import org.apache.shiro.authz.annotation.RequiresPermissions
import org.hibernate.validator.constraints.NotEmpty

/**
 * Repository {@link DirectComponent}.
 *
 * @since 3.0
 */
@Named
@Singleton
@DirectAction(action = 'coreui_Repository')
class RepositoryComponent
extends DirectComponentSupport
{
  @Inject
  RepositoryManager repositoryManager

  @Inject
  Map<String, Recipe> recipes

  @DirectMethod
  List<RepositoryXO> read() {
    repositoryManager.browse().collect { asRepository(it) }
  }

  @DirectMethod
  List<ReferenceXO> readRecipes() {
    recipes.collect { key, value ->
      new ReferenceXO(
          id: key,
          name: "${value.format} (${value.type})"
      )
    }
  }

  /**
   * Retrieve a list of available repositories references.
   */
  @DirectMethod
  @RequiresPermissions('nexus:repositories:read')
  List<RepositoryReferenceXO> readReferences(final @Nullable StoreLoadParameters parameters) {
    return filter(parameters).collect { Repository repository ->
      new RepositoryReferenceXO(
          id: repository.name,
          name: repository.name,
          type: repository.type.toString(),
          format: repository.format.toString()
      )
    }
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate(groups = [Create.class, Default.class])
  RepositoryXO create(final @NotNull(message = '[repository] may not be null') @Valid RepositoryXO repository) {
    return asRepository(repositoryManager.create(new Configuration(
        repositoryName: repository.name,
        recipeName: repository.recipe,
        online: repository.online,
        attributes: repository.attributes
    )))
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate(groups = [Update.class, Default.class])
  RepositoryXO update(final @NotNull(message = '[repository] may not be null') @Valid RepositoryXO repository) {
    return asRepository(repositoryManager.update(repositoryManager.get(repository.name).configuration.with {
      online = repository.online
      attributes = repository.attributes
      return it
    }))
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate
  void remove(final @NotEmpty(message = '[name] may not be empty') String name) {
    repositoryManager.delete(name)
  }

  RepositoryXO asRepository(Repository input) {
    return new RepositoryXO(
        name: input.name,
        type: input.type,
        format: input.format,
        online: input.configuration.online,
        recipe: input.configuration.recipeName,
        status: buildStatus(input),
        attributes: input.configuration.attributes,
        url: "${BaseUrlHolder.get()}/repository/${input.name}/" // trailing slash is important
    )
  }

  @DirectPollMethod(event = "coreui_Repository_readStatus")
  @RequiresAuthentication
  List<RepositoryStatusXO> readStatus(final Map<String, String> params) {
    repositoryManager.browse().collect { Repository repository -> buildStatus(repository) }
  }

  RepositoryStatusXO buildStatus(Repository input) {
    RepositoryStatusXO statusXO = new RepositoryStatusXO(repositoryName: input.name,
        online: input.configuration.online)

    try {
      if (input.facet(GroupFacet)) {
        //TODO - should we try to aggregate status from group members?
        return statusXO
      }
    }
    catch (MissingFacetException e) {
      // no group, can refine status
    }

    try {
      def remoteStatus = input.facet(HttpClientFacet).status
      statusXO.description = remoteStatus.description
      if (remoteStatus.reason) {
        statusXO.reason = remoteStatus.reason
      }
    }
    catch (MissingFacetException e) {
      // no proxy, no remote status
    }
    return statusXO
  }

  @PackageScope
  List<Repository> filter(final @Nullable StoreLoadParameters parameters) {
    def repositories = repositoryManager.browse()
    if (parameters) {
      String format = parameters.getFilter('format')
      if (format) {
        return repositories.findResults { Repository repository ->
          repository.format == format
        }
      }
    }
    return repositories
  }
}
