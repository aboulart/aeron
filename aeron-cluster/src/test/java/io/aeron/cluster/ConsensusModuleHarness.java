/*
 * Copyright 2014-2018 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.CommonContext.ENDPOINT_PARAM_NAME;

public class ConsensusModuleHarness implements AutoCloseable, ClusteredService
{
    private final ClusteredMediaDriver clusteredMediaDriver;
    private final ClusteredServiceContainer clusteredServiceContainer;
    private final AtomicBoolean isTerminated = new AtomicBoolean();
    private final Aeron aeron;
    private final ClusteredService service;
    private final AtomicBoolean serviceOnStart = new AtomicBoolean();
    private final IdleStrategy idleStrategy = new SleepingIdleStrategy(1);
    private final ClusterMember[] members;
    private final MemberStatusAdapter[] memberStatusAdapters;
    private final Publication[] memberStatusPublications;
    private final MemberStatusPublisher memberStatusPublisher = new MemberStatusPublisher();
    private int thisMemberIndex = -1;
    private int leaderIndex = -1;

    ConsensusModuleHarness(
        final ConsensusModule.Context context,
        final ClusteredService service,
        final MemberStatusListener[] memberStatusListeners,
        final boolean isCleanStart)
    {
        clusteredMediaDriver = ClusteredMediaDriver.launch(
            new MediaDriver.Context()
                .warnIfDirectoryExists(isCleanStart)
                .threadingMode(ThreadingMode.SHARED)
                .termBufferSparseFile(true)
                .errorHandler(Throwable::printStackTrace)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(isCleanStart),
            context
                .terminationHook(() -> isTerminated.set(true))
                .deleteDirOnStart(isCleanStart));

        clusteredServiceContainer = ClusteredServiceContainer.launch(
            new ClusteredServiceContainer.Context()
                .idleStrategySupplier(() -> new SleepingIdleStrategy(1))
                .clusteredService(this)
                .terminationHook(() -> {})
                .errorHandler(Throwable::printStackTrace)
                .deleteDirOnStart(isCleanStart));

        this.service = service;
        aeron = Aeron.connect();

        members = ClusterMember.parse(context.clusterMembers());

        memberStatusAdapters = new MemberStatusAdapter[members.length];
        memberStatusPublications = new Publication[members.length];

        for (int i = 0; i < members.length; i++)
        {
            if (context.clusterMemberId() != members[i].id())
            {
                final ChannelUri memberStatusUri = ChannelUri.parse(context.memberStatusChannel());
                memberStatusUri.put(ENDPOINT_PARAM_NAME, members[i].memberFacingEndpoint());

                final int statusStreamId = context.memberStatusStreamId();
                memberStatusAdapters[i] = new MemberStatusAdapter(
                    aeron.addSubscription(memberStatusUri.toString(), statusStreamId), memberStatusListeners[i]);
                memberStatusPublications[i] =
                    aeron.addExclusivePublication(context.memberStatusChannel(), context.memberStatusStreamId());
            }
            else
            {
                thisMemberIndex = i;
            }

            if (members[i].id() == context.appointedLeaderId())
            {
                leaderIndex = i;
            }
        }

        if (members.length > 0 && thisMemberIndex != leaderIndex)
        {
            // TODO: need to create Leader archive for possible catchUp
        }
    }

    public void close()
    {
        CloseHelper.close(aeron);
        CloseHelper.close(clusteredServiceContainer);
        CloseHelper.close(clusteredMediaDriver);
        deleteDirectories();
    }

    public void deleteDirectories()
    {
        if (null != clusteredServiceContainer)
        {
            clusteredServiceContainer.context().deleteDirectory();
        }

        if (null != clusteredMediaDriver)
        {
            clusteredMediaDriver.mediaDriver().context().deleteAeronDirectory();
            clusteredMediaDriver.consensusModule().context().deleteDirectory();
            clusteredMediaDriver.archive().context().deleteArchiveDirectory();
        }
    }

    public void pollMemberStatusAdapters()
    {
        for (final MemberStatusAdapter adapter : memberStatusAdapters)
        {
            if (null != adapter)
            {
                adapter.poll();
            }
        }
    }

    public Publication memberStatusPublication(final int index)
    {
        return memberStatusPublications[index];
    }

    public MemberStatusPublisher memberStatusPublisher()
    {
        return memberStatusPublisher;
    }

    public void awaitServiceOnStart()
    {
        idleStrategy.reset();
        while (serviceOnStart.get())
        {
            idleStrategy.idle();
        }
    }

    public void onStart(final Cluster cluster)
    {
        service.onStart(cluster);
        serviceOnStart.lazySet(true);
    }

    public void onSessionOpen(final ClientSession session, final long timestampMs)
    {
        service.onSessionOpen(session, timestampMs);
    }

    public void onSessionClose(final ClientSession session, final long timestampMs, final CloseReason closeReason)
    {
        service.onSessionClose(session, timestampMs, closeReason);
    }

    public void onSessionMessage(
        final long clusterSessionId,
        final long correlationId,
        final long timestampMs,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        service.onSessionMessage(clusterSessionId, correlationId, timestampMs, buffer, offset, length, header);
    }

    public void onTimerEvent(final long correlationId, final long timestampMs)
    {
        service.onTimerEvent(correlationId, timestampMs);
    }

    public void onTakeSnapshot(final Publication snapshotPublication)
    {
        service.onTakeSnapshot(snapshotPublication);
    }

    public void onLoadSnapshot(final Image snapshotImage)
    {
        service.onLoadSnapshot(snapshotImage);
    }

    public void onReplayBegin()
    {
        service.onReplayBegin();
    }

    public void onReplayEnd()
    {
        service.onReplayEnd();
    }

    public void onRoleChange(final Cluster.Role newRole)
    {
        service.onRoleChange(newRole);
    }

    public void onReady()
    {
        service.onReady();
    }
}
