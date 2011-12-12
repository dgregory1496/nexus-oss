/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions
 *
 * This program is free software: you can redistribute it and/or modify it only under the terms of the GNU Affero General
 * Public License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License Version 3
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License Version 3 along with this program.  If not, see
 * http://www.gnu.org/licenses.
 *
 * Sonatype Nexus (TM) Open Source Version is available from Sonatype, Inc. Sonatype and Sonatype Nexus are trademarks of
 * Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation. M2Eclipse is a trademark of the Eclipse Foundation.
 * All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.storage.remote.ahc;

import java.net.URL;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.nexus.ahc.AhcProvider;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.RemoteAccessDeniedException;
import org.sonatype.nexus.proxy.RemoteAccessException;
import org.sonatype.nexus.proxy.RemoteAuthenticationNeededException;
import org.sonatype.nexus.proxy.RemoteStorageException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.AbstractStorageItem;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.PreparedContentLocator;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.repository.ProxyRepository;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.proxy.storage.remote.AbstractRemoteRepositoryStorage;
import org.sonatype.nexus.proxy.storage.remote.DefaultRemoteStorageContext.BooleanFlagHolder;
import org.sonatype.nexus.proxy.storage.remote.RemoteRepositoryStorage;
import org.sonatype.nexus.proxy.storage.remote.RemoteStorageContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.BodyDeferringAsyncHandler.BodyDeferringInputStream;
import com.ning.http.client.Response;

/**
 * AsyncHttpClient powered RemoteRepositoryStorage.
 * 
 * @author cstamas
 */
@Component( role = RemoteRepositoryStorage.class, hint = AhcRemoteRepositoryStorage.PROVIDER_STRING )
public class AhcRemoteRepositoryStorage
    extends AbstractRemoteRepositoryStorage
{
    public static final String PROVIDER_STRING = "async-http-client";

    private static final String CTX_KEY = PROVIDER_STRING;

    private static final String CTX_KEY_CLIENT = CTX_KEY + ".client";

    private static final String CTX_KEY_S3_FLAG = CTX_KEY + ".remoteIsAmazonS3";

    @Requirement
    private AhcProvider ahcProvider;

    @Override
    public String getProviderId()
    {
        return PROVIDER_STRING;
    }

    @Override
    public AbstractStorageItem retrieveItem( ProxyRepository repository, ResourceStoreRequest request, String baseUrl )
        throws ItemNotFoundException, RemoteAccessException, RemoteStorageException
    {
        final URL remoteURL = getAbsoluteUrlFromBase( baseUrl, request.getRequestPath() );

        final String itemUrl = remoteURL.toString();

        final AsyncHttpClient client = getClient( repository );

        try
        {

            BodyDeferringInputStream ris = AHCUtils.fetchContent( client, itemUrl );

            // this blocks until response headers arrived
            Response response = ris.getAsapResponse();

            // expected: 200 OK
            validateResponse( repository, request, "GET", itemUrl, response, 200 );

            long length = AHCUtils.getContentLength( response, -1 );

            long lastModified = AHCUtils.getLastModified( response, System.currentTimeMillis() );

            // non-reusable simplest content locator, the ris InputStream is ready to be consumed
            PreparedContentLocator contentLocator = new PreparedContentLocator( ris, response.getContentType() );

            DefaultStorageFileItem result =
                new DefaultStorageFileItem( repository, request, true /* canRead */, true /* canWrite */,
                    contentLocator );

            result.setLength( length );

            result.setModified( lastModified );

            result.setCreated( result.getModified() );

            result.setRemoteUrl( itemUrl );

            result.getItemContext().setParentContext( request.getRequestContext() );

            return result;
        }
        catch ( ItemNotFoundException e )
        {
            throw e;
        }
        catch ( RemoteStorageException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new RemoteStorageException( e );
        }
    }

    @Override
    public void storeItem( ProxyRepository repository, StorageItem item )
        throws UnsupportedStorageOperationException, RemoteAccessException, RemoteStorageException
    {
        if ( !( item instanceof StorageFileItem ) )
        {
            throw new UnsupportedStorageOperationException( "Storing of non-files remotely is not supported!" );
        }

        final StorageFileItem fItem = (StorageFileItem) item;

        final ResourceStoreRequest request = new ResourceStoreRequest( item );

        final URL remoteURL = getAbsoluteUrlFromBase( repository, request );

        final String itemUrl = remoteURL.toString();

        final AsyncHttpClient client = getClient( repository );

        try
        {
            Response response =
                client.preparePut( itemUrl ).setBody( new StorageFileItemBodyGenerator( fItem ) ).execute().get();

            // expected: 200 OK, 201 CREATED, 202 ACCEPTED, 204 NO_CONTENT
            validateResponse( repository, request, "PUT", itemUrl, response, 200, 201, 202, 204 );
        }
        catch ( ItemNotFoundException e )
        {
            // rather unexpected response
            throw new RemoteStorageException( e );
        }
        catch ( RemoteStorageException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new RemoteStorageException( e );
        }
    }

    @Override
    public void deleteItem( ProxyRepository repository, ResourceStoreRequest request )
        throws ItemNotFoundException, UnsupportedStorageOperationException, RemoteAccessException,
        RemoteStorageException
    {
        final URL remoteURL = getAbsoluteUrlFromBase( repository, request );

        final String itemUrl = remoteURL.toString();

        final AsyncHttpClient client = getClient( repository );

        try
        {
            Response response = client.prepareDelete( itemUrl ).execute().get();

            // expected: 200 OK, 202 ACCEPTED, 204 NO_CONTENT
            validateResponse( repository, request, "DELETE", itemUrl, response, 200, 202, 204 );
        }
        catch ( ItemNotFoundException e )
        {
            throw e;
        }
        catch ( RemoteStorageException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new RemoteStorageException( e );
        }
    }

    protected void validateResponse( final ProxyRepository repository, final ResourceStoreRequest request,
                                     final String method, final String remoteUrl, final Response response,
                                     int... expectedCodes )
        throws ItemNotFoundException, RemoteStorageException
    {
        // maintain the S3 flag
        checkForRemotePeerAmazonS3Storage( repository, response.getHeader( "server" ) );

        if ( response.isRedirected() )
        {
            getLogger().info(
                String.format(
                    "Proxy repository %s (id=%s) got redirected from %s, please verify your remoteUrl is up-to-date!",
                    repository.getName(), repository.getId(), remoteUrl ) );
        }

        if ( AHCUtils.isAnyOfTheseStatusCodes( response, expectedCodes ) )
        {
            // good, an expected one
            return;
        }

        // 404 NotFound
        if ( 404 == response.getStatusCode() )
        {
            throw new ItemNotFoundException( request, repository );
        }

        // 401 Unauthorized
        if ( 401 == response.getStatusCode() )
        {
            throw new RemoteAuthenticationNeededException( repository, remoteUrl, response.getStatusText() );
        }

        // 403 Forbidden
        if ( 403 == response.getStatusCode() )
        {
            throw new RemoteAccessDeniedException( repository, remoteUrl, response.getStatusText() );
        }

        // anything else "unexpected"?
        throw new RemoteStorageException( String.format(
            "Coult not perform %s against Url %s, unexpected response is %s", method, remoteUrl,
            response.getStatusText() ) );
    }

    protected AsyncHttpClient getClient( final ProxyRepository repository )
        throws RemoteStorageException
    {
        RemoteStorageContext ctx = getRemoteStorageContext( repository );

        AsyncHttpClient httpClient = (AsyncHttpClient) ctx.getContextObject( CTX_KEY_CLIENT );

        return httpClient;
    }

    @Override
    protected void updateContext( ProxyRepository repository, RemoteStorageContext context )
        throws RemoteStorageException
    {
        getLogger().info(
            "Remote storage settings change detected for ProxyRepository ID=\"" + repository.getId() + "\" (\""
                + repository.getName() + "\"), updating HTTP transport..." );

        if ( context.hasContextObject( CTX_KEY_CLIENT ) )
        {
            // proper shutdown of AHC, but cannot call getClient() here that would result in endless loop!
            AsyncHttpClient oldClient = (AsyncHttpClient) context.getContextObject( CTX_KEY_CLIENT );

            // TODO: AHC-26: current solution would kill ongoing downloads, be smarter
            oldClient.close();
        }

        final AsyncHttpClientConfig.Builder clientConfigBuilder = ahcProvider.getAsyncHttpClientConfigBuilder( repository, context );

        final AsyncHttpClient client = new AsyncHttpClient( clientConfigBuilder.build() );

        context.putContextObject( CTX_KEY_CLIENT, client );

        context.putContextObject( CTX_KEY_S3_FLAG, new BooleanFlagHolder() );
    }

    @Override
    protected boolean checkRemoteAvailability( long newerThen, ProxyRepository repository,
                                               ResourceStoreRequest request, boolean isStrict )
        throws RemoteStorageException
    {
        final URL remoteURL = getAbsoluteUrlFromBase( repository, request );

        final String itemUrl = remoteURL.toString();

        final AsyncHttpClient client = getClient( repository );

        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug(
                String.format( "Checking remote availability of proxy repository \"%s\" (id=%s) on URL %s",
                    repository.getName(), repository.getId(), itemUrl ) );
        }

        // artifactory hack, it pukes on HEAD so we will try with GET if HEAD fails
        boolean doGet = false;

        Response responseObject = null;

        int response = 400;

        try
        {
            responseObject = client.prepareHead( itemUrl ).execute().get();

            response = responseObject.getStatusCode();

            validateResponse( repository, request, "HEAD", itemUrl, responseObject, 200 );
        }
        catch ( ItemNotFoundException e )
        {
            return false;
        }
        catch ( RemoteStorageException e )
        {
            // If HEAD failed, attempt a GET. Some repos may not support HEAD method
            doGet = true;

            getLogger().debug( "HEAD method failed, will attempt GET.  Exception: " + e.getMessage(), e );
        }
        catch ( Exception e )
        {
            throw new RemoteStorageException( e );
        }
        finally
        {
            // HEAD returned error, but not exception, try GET before failing
            if ( !doGet && response != 200 )
            {
                // try with GET unless some known to fail responses are in
                doGet = ( response != 401 ) && ( response != 403 );

                getLogger().debug( "HEAD method failed, will attempt GET.  Status: " + response );
            }
        }

        if ( doGet )
        {
            try
            {
                responseObject = client.prepareGet( itemUrl ).execute().get();

                response = responseObject.getStatusCode();

                validateResponse( repository, request, "GET", itemUrl, responseObject, 200 );
            }
            catch ( ItemNotFoundException e )
            {
                return false;
            }
            catch ( Exception e )
            {
                throw new RemoteStorageException( e );
            }
        }

        // if we are not strict and remote is S3
        if ( !isStrict && isRemotePeerAmazonS3Storage( repository ) )
        {
            // if we are relaxed, we will accept any HTTP response code below 500. This means anyway the HTTP
            // transaction succeeded. This method was never really detecting that the remoteUrl really denotes a root of
            // repository (how could we do that?)
            // this "relaxed" check will help us to "pass" S3 remote storage.
            return response >= 200 && response < 500;
        }
        else
        {
            // non relaxed check is strict, and will select only the OK response
            if ( response == 200 )
            {
                // we have it
                // we have newer if this below is true
                return AHCUtils.getLastModified( responseObject, System.currentTimeMillis() ) > newerThen;
            }
            else if ( ( response >= 300 && response < 400 ) || response == 404 )
            {
                return false;
            }
            else
            {
                throw new RemoteStorageException( "Unexpected response code while executing GET"
                    + " method [repositoryId=\"" + repository.getId() + "\", requestPath=\"" + request.getRequestPath()
                    + "\", remoteUrl=\"" + itemUrl + "\"]. Expected: \"SUCCESS (200)\". Received: " + response + " : "
                    + responseObject.getStatusText() );
            }
        }
    }

    @Override
    protected String getS3FlagKey()
    {
        return CTX_KEY_S3_FLAG;
    }

}
