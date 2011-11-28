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
package org.sonatype.nexus.integrationtests.nexus4341;

import static org.sonatype.sisu.goodies.common.Time.time;
import static org.sonatype.tests.http.server.fluent.Behaviours.error;
import static org.sonatype.tests.http.server.fluent.Behaviours.pause;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.restlet.data.Status;
import org.sonatype.jettytestsuite.ServletServer;
import org.sonatype.nexus.integrationtests.AbstractNexusProxyIntegrationTest;
import org.sonatype.nexus.rest.model.ScheduledServiceBaseResource;
import org.sonatype.nexus.rest.model.ScheduledServiceListResource;
import org.sonatype.nexus.rest.model.ScheduledServicePropertyResource;
import org.sonatype.nexus.tasks.descriptors.DownloadIndexesTaskDescriptor;
import org.sonatype.nexus.test.utils.TaskScheduleUtil;
import org.sonatype.tests.http.server.fluent.Server;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class Nexus4341RunningTaskNotEditableIT
    extends AbstractNexusProxyIntegrationTest
{

    private Server return500Server;

    private void replaceServer()
        throws Exception
    {
        ServletServer server = (ServletServer) this.lookup( ServletServer.ROLE );
        server.stop();
        int port = server.getPort();

        return500Server =
            Server.withPort( port ).serve( "/*" ).withBehaviours( pause( time( 30, TimeUnit.SECONDS ) ), error( 500 ) ).start();
    }

    @AfterMethod
    public void stopServer()
        throws Exception
    {
        if ( return500Server != null )
        {
            return500Server.stop();
        }
    }

    private void createDownloadIndexesTask( String name )
        throws Exception
    {
        ScheduledServiceBaseResource scheduledTask = getScheduledTaskTemplate( name );

        Status status = TaskScheduleUtil.create( scheduledTask );

        Assert.assertTrue( status.isSuccess() );
    }

    private void verifyNoUpdate( ScheduledServiceListResource resource )
        throws IOException
    {
        System.err.println( String.format( "Trying to update %s (%s)", resource.getName(), resource.getStatus() ) );
        ScheduledServiceBaseResource changed = getScheduledTaskTemplate( "changed" );
        changed.setId( resource.getId() );
        Status status = TaskScheduleUtil.update( changed );
        Assert.assertTrue( status.isClientError(), "Should not have been able to update task with state "
            + resource.getStatus() + ", " + status.getDescription() );
    }

    private ScheduledServiceBaseResource getScheduledTaskTemplate( String name )
    {
        ScheduledServicePropertyResource repositoryProp = new ScheduledServicePropertyResource();
        repositoryProp.setKey( DownloadIndexesTaskDescriptor.REPO_OR_GROUP_FIELD_ID );
        repositoryProp.setValue( getTestRepositoryId() );

        ScheduledServiceBaseResource scheduledTask = new ScheduledServiceBaseResource();
        scheduledTask.setEnabled( true );
        scheduledTask.setId( null );
        scheduledTask.setName( name );
        scheduledTask.setTypeId( DownloadIndexesTaskDescriptor.ID );
        scheduledTask.setSchedule( "manual" );
        scheduledTask.addProperty( repositoryProp );
        return scheduledTask;
    }

    @Test
    public void testNoUpdateForRunningTasks()
        throws Exception
    {
        replaceServer();

        Thread.sleep( 1000 );

        createDownloadIndexesTask( "Nexus4341Task1" );
        createDownloadIndexesTask( "Nexus4341Task2" );

        List<ScheduledServiceListResource> tasks = TaskScheduleUtil.getTasks();

        Assert.assertEquals( 2, tasks.size() );

        for ( ScheduledServiceListResource resource : tasks )
        {
            log.debug( "Starting task " + resource.getName() );
            Status status = TaskScheduleUtil.run( resource.getId() );
            Assert.assertTrue( status.isSuccess() );
        }

        int ticks = 1;
        while ( ticks <= 60 )
        {
            tasks = TaskScheduleUtil.getTasks();
            Assert.assertEquals( tasks.size(), 2 );
            if ( tasks.get( 0 ).getStatus().equals( "RUNNING" ) && tasks.get( 1 ).getStatus().equals( "SLEEPING" ) )
            {
                break;
            }
            else if ( tasks.get( 1 ).getStatus().equals( "RUNNING" ) && tasks.get( 0 ).getStatus().equals( "SLEEPING" ) )
            {
                break;
            }
            
            ticks++;
            Thread.yield();
            Thread.sleep( 1000 );
        }

        boolean triedSleeping = false;
        boolean triedRunning = false;
        boolean triedCancelling = false;

        for ( ScheduledServiceListResource resource : TaskScheduleUtil.getAllTasks() )
        {
            System.err.println( "Found task with state " + resource.getStatus() );
            if ( resource.getStatus().equals( "SLEEPING" ) )
            {
                verifyNoUpdate( resource );
                TaskScheduleUtil.cancel( resource.getId() );
                triedSleeping = true;
            }
            else if ( resource.getStatus().equals( "RUNNING" ) )
            {
                verifyNoUpdate( resource );
                triedRunning = true;

                // Cancel running task and try to update again
                TaskScheduleUtil.cancel( resource.getId() );
                resource = TaskScheduleUtil.getTask( resource.getName() );
                Assert.assertEquals( resource.getStatus(), "CANCELLING" );

                verifyNoUpdate( resource );
                triedCancelling = true;
            }
        }

        Assert.assertTrue( triedSleeping, "Did not see state SLEEPING" );
        Assert.assertTrue( triedRunning, "Did not see state RUNNING" );
        Assert.assertTrue( triedCancelling, "Did not see state CANCELLING" );
    }
}
