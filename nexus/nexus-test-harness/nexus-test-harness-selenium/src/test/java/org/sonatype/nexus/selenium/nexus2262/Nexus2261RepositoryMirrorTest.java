package org.sonatype.nexus.selenium.nexus2262;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.data.Status;
import org.sonatype.nexus.Nexus;
import org.sonatype.nexus.configuration.model.CLocalStorage;
import org.sonatype.nexus.configuration.model.CRemoteStorage;
import org.sonatype.nexus.configuration.model.CRepository;
import org.sonatype.nexus.mock.MockResponse;
import org.sonatype.nexus.mock.SeleniumTest;
import org.sonatype.nexus.mock.pages.RepositoryMirror;
import org.sonatype.nexus.mock.pages.RepositoriesEditTabs.RepoKind;
import org.sonatype.nexus.mock.rest.MockHelper;
import org.sonatype.nexus.proxy.maven.RepositoryPolicy;
import org.sonatype.nexus.proxy.maven.maven2.M2Repository;
import org.sonatype.nexus.proxy.maven.maven2.M2RepositoryConfiguration;
import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.proxy.repository.LocalStatus;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.remote.commonshttpclient.CommonsHttpClientRemoteStorage;
import org.sonatype.nexus.rest.model.MirrorResource;
import org.sonatype.nexus.rest.model.MirrorResourceListResponse;
import org.sonatype.nexus.selenium.nexus1815.LoginTest;
import org.sonatype.nexus.selenium.util.NxAssert;

public class Nexus2261RepositoryMirrorTest
    extends SeleniumTest
{

    private M2Repository repo;

    private Nexus nexus;

    private RepositoryRegistry repositoryRegistry;

    @Before
    public void createRepo()
        throws Exception
    {
        nexus = lookup( Nexus.class );
        repositoryRegistry = lookup( RepositoryRegistry.class );
        CRepository cRepo = new CRepository();
        cRepo.setId( "nexus2261" );
        cRepo.setName( "nexus2261" );
        cRepo.setProviderRole( Repository.class.getName() );
        cRepo.setProviderHint( "maven2" );

        cRepo.setLocalStatus( LocalStatus.IN_SERVICE.name() );

        Xpp3Dom ex = new Xpp3Dom( "externalConfiguration" );
        cRepo.setExternalConfiguration( ex );
        M2RepositoryConfiguration exConf = new M2RepositoryConfiguration( ex );
        exConf.setRepositoryPolicy( RepositoryPolicy.RELEASE );
        exConf.applyChanges();

        cRepo.setLocalStorage( new CLocalStorage() );
        cRepo.getLocalStorage().setUrl( cRepo.defaultLocalStorageUrl );
        cRepo.getLocalStorage().setProvider( "file" );

        cRepo.setRemoteStorage( new CRemoteStorage() );
        cRepo.getRemoteStorage().setProvider( CommonsHttpClientRemoteStorage.PROVIDER_STRING );
        cRepo.getRemoteStorage().setUrl( "http://some-remote-repository/repo-root" );
        cRepo.setExposed( true );
        cRepo.setUserManaged( true );
        cRepo.setIndexable( true );
        cRepo.setBrowseable( true );

        repo = (M2Repository) nexus.createRepository( cRepo );
    }

    @After
    public void deleteRepo()
        throws Exception
    {
        nexus.deleteRepository( repo.getId() );
    }

    @Test
    public void fieldValidation()
        throws Exception
    {
        LoginTest.doLogin( main );

        RepositoryMirror mirror = main.openRepositories().select( repo.getId(), RepoKind.PROXY ).selectMirror();

        // invalid mirror format
        mirror.addMirror( "mock-mirror" );
        NxAssert.hasErrorText( mirror.getMirrorUrl(), "Protocol must be http:// or https://" );
        mirror.addMirror( "http://www.sonatype.org" );
        NxAssert.noErrorText( mirror.getMirrorUrl() );

        // duplicated validation
        mirror.addMirror( "http://www.sonatype.org" );
        NxAssert.hasErrorText( mirror.getMirrorUrl(), "This URL already exists" );

        mirror.addMirror( "https://www.sonatype.org" );
        NxAssert.noErrorText( mirror.getMirrorUrl() );

        mirror.cancel();
    }

    @Test
    public void mirrorProxy()
        throws Exception
    {
        LoginTest.doLogin( main );

        MirrorResourceListResponse mirrors = new MirrorResourceListResponse();
        MirrorResource m = new MirrorResource();
        m.setId( "mock-mirror" );
        m.setUrl( "http://mock-remove-mirror-url/repo" );
        mirrors.addData( m );

        MockHelper.expect( "/repository_predefined_mirrors/{repositoryId}", new MockResponse( Status.SUCCESS_OK,
                                                                                              mirrors ) );
        RepositoryMirror mirror = main.openRepositories().select( repo.getId(), RepoKind.PROXY ).selectMirror();

        mirror.addMirror( "mock-mirror" ).addMirror( "http://www.sonatype.org" ).save();

        Assert.assertNotNull( repo.getDownloadMirrors() );
        Assert.assertNotNull( repo.getDownloadMirrors().getMirrors() );
        Assert.assertEquals( 2, repo.getDownloadMirrors().getMirrors().size() );

        mirror.removeMirror( "http://www.sonatype.org" ).save();
        Assert.assertEquals( 1, repo.getDownloadMirrors().getMirrors().size() );

        mirror.removeAllMirrors().save();
        Assert.assertEquals( 0, repo.getDownloadMirrors().getMirrors().size() );
    }

}
