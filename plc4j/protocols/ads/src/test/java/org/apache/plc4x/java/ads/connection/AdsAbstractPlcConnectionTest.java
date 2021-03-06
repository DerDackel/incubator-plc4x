/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */
package org.apache.plc4x.java.ads.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.plc4x.java.ads.api.commands.AdsReadWriteResponse;
import org.apache.plc4x.java.ads.api.commands.types.Data;
import org.apache.plc4x.java.ads.api.commands.types.Result;
import org.apache.plc4x.java.ads.api.generic.types.AmsNetId;
import org.apache.plc4x.java.ads.api.generic.types.AmsPort;
import org.apache.plc4x.java.ads.model.AdsAddress;
import org.apache.plc4x.java.ads.model.SymbolicAdsAddress;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.api.messages.*;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcReadRequest;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcReadResponse;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcWriteRequest;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcWriteResponse;
import org.apache.plc4x.java.api.model.Address;
import org.apache.plc4x.java.base.connection.ChannelFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

import static org.apache.plc4x.java.base.util.Junit5Backport.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
public class AdsAbstractPlcConnectionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdsAbstractPlcConnectionTest.class);

    private AdsAbstractPlcConnection SUT;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChannelFactory channelFactory;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Channel channel;

    @Before
    public void setUp() throws Exception {
        SUT = new AdsAbstractPlcConnection(channelFactory, mock(AmsNetId.class), mock(AmsPort.class), mock(AmsNetId.class), mock(AmsPort.class)) {
            @Override
            protected ChannelHandler getChannelHandler(CompletableFuture<Void> sessionSetupCompleteFuture) {
                return null;
            }
        };

        when(channelFactory.createChannel(any())).thenReturn(channel);

        SUT.connect();
    }

    @Test
    public void lazyConstructor() {
        AdsAbstractPlcConnection constructed = new AdsAbstractPlcConnection(channelFactory, mock(AmsNetId.class), mock(AmsPort.class)) {
            @Override
            protected ChannelHandler getChannelHandler(CompletableFuture<Void> sessionSetupCompleteFuture) {
                return null;
            }
        };
        assertEquals(AdsAbstractPlcConnection.generateAMSNetId(), constructed.getSourceAmsNetId());
        assertEquals(AdsAbstractPlcConnection.generateAMSPort(), constructed.getSourceAmsPort());
    }

    @Test
    public void getTargetAmsNetId() {
        AmsNetId targetAmsNetId = SUT.getTargetAmsNetId();
        assertNotNull(targetAmsNetId);
    }

    @Test
    public void getTargetAmsPort() {
        AmsPort targetAmsPort = SUT.getTargetAmsPort();
        assertNotNull(targetAmsPort);
    }

    @Test
    public void getSourceAmsNetId() {
        AmsNetId sourceAmsNetId = SUT.getSourceAmsNetId();
        assertNotNull(sourceAmsNetId);
    }

    @Test
    public void getSourceAmsPort() {
        AmsPort sourceAmsPort = SUT.getSourceAmsPort();
        assertNotNull(sourceAmsPort);
    }

    @Test
    public void parseAddress() {
        Address address = SUT.parseAddress("0/0");
        assertNotNull(address);
        Address SymbolicAddress = SUT.parseAddress("Main.byByte[0]");
        assertNotNull(SymbolicAddress);
    }

    @Test
    public void read() {
        CompletableFuture<PlcReadResponse> read = SUT.read(mock(PlcReadRequest.class));
        assertNotNull(read);
        CompletableFuture<TypeSafePlcReadResponse<Object>> typeSafeRead = SUT.read(mock(TypeSafePlcReadRequest.class));
        assertNotNull(typeSafeRead);

        simulatePipelineError(() -> SUT.read(mock(PlcReadRequest.class)));
        simulatePipelineError(() -> SUT.read(mock(TypeSafePlcReadRequest.class)));
    }

    @Test
    public void write() {
        CompletableFuture<PlcWriteResponse> write = SUT.write(mock(PlcWriteRequest.class));
        assertNotNull(write);
        CompletableFuture<TypeSafePlcWriteResponse<Object>> typeSafeWrite = SUT.write(mock(TypeSafePlcWriteRequest.class));
        assertNotNull(typeSafeWrite);

        simulatePipelineError(() -> SUT.write(mock(PlcWriteRequest.class)));
        simulatePipelineError(() -> SUT.write(mock(TypeSafePlcWriteRequest.class)));
    }

    @Test
    public void send() {
        CompletableFuture send = SUT.send(mock(PlcProprietaryRequest.class));
        assertNotNull(send);

        simulatePipelineError(() -> SUT.send(mock(PlcProprietaryRequest.class)));
    }

    public void simulatePipelineError(FutureProducingTestRunnable futureProducingTestRunnable) {
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        // Simulate error in the pipeline
        when(channelFuture.addListener(any())).thenAnswer(invocation -> {
            Future future = mock(Future.class);
            when(future.isSuccess()).thenReturn(false);
            when(future.cause()).thenReturn(new DummyException());
            GenericFutureListener genericFutureListener = invocation.getArgument(0);
            genericFutureListener.operationComplete(future);
            return mock(ChannelFuture.class);
        });
        when(channel.writeAndFlush(any())).thenReturn(channelFuture);
        assertThrows(DummyException.class, () -> {
            CompletableFuture completableFuture = futureProducingTestRunnable.run();
            try {
                completableFuture.get(3, TimeUnit.SECONDS);
                fail("Should have thrown a ExecutionException");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof DummyException) {
                    throw (DummyException) e.getCause();
                }
                throw e;
            }
        });
    }

    @Test
    public void mapAddresses() {
        SUT.mapAddresses(mock(PlcRequest.class));
    }

    @Test
    public void mapAddress() {
        // positive
        {
            when(channel.writeAndFlush(any(PlcRequestContainer.class))).then(invocation -> {
                PlcRequestContainer plcRequestContainer = invocation.getArgument(0);
                PlcProprietaryResponse plcProprietaryResponse = mock(PlcProprietaryResponse.class, RETURNS_DEEP_STUBS);
                AdsReadWriteResponse adsReadWriteResponse = mock(AdsReadWriteResponse.class, RETURNS_DEEP_STUBS);
                when(adsReadWriteResponse.getResult()).thenReturn(Result.of(0));
                when(adsReadWriteResponse.getData()).thenReturn(Data.of(new byte[]{1, 2, 3, 4}));
                when(plcProprietaryResponse.getResponse()).thenReturn(adsReadWriteResponse);
                plcRequestContainer.getResponseFuture().complete(plcProprietaryResponse);
                return mock(ChannelFuture.class);
            });

            SUT.mapAddress(SymbolicAdsAddress.of("Main.byByte[0]"));
            SUT.mapAddress(SymbolicAdsAddress.of("Main.byByte[0]"));
            verify(channel, times(1)).writeAndFlush(any(PlcRequestContainer.class));
            SUT.clearMapping();
            reset(channel);
        }
        // negative
        {
            when(channel.writeAndFlush(any(PlcRequestContainer.class))).then(invocation -> {
                PlcRequestContainer plcRequestContainer = invocation.getArgument(0);
                PlcProprietaryResponse plcProprietaryResponse = mock(PlcProprietaryResponse.class, RETURNS_DEEP_STUBS);
                AdsReadWriteResponse adsReadWriteResponse = mock(AdsReadWriteResponse.class, RETURNS_DEEP_STUBS);
                when(adsReadWriteResponse.getResult()).thenReturn(Result.of(1));
                when(plcProprietaryResponse.getResponse()).thenReturn(adsReadWriteResponse);
                plcRequestContainer.getResponseFuture().complete(plcProprietaryResponse);
                return mock(ChannelFuture.class);
            });

            assertThrows(PlcRuntimeException.class, () -> SUT.mapAddress(SymbolicAdsAddress.of("Main.byByte[0]")));
            verify(channel, times(1)).writeAndFlush(any(PlcRequestContainer.class));
            SUT.clearMapping();
            reset(channel);
        }
    }

    @Test
    public void generateAMSNetId() {
        AmsNetId targetAmsNetId = AdsAbstractPlcConnection.generateAMSNetId();
        assertNotNull(targetAmsNetId);
    }

    @Test
    public void generateAMSPort() {
        AmsPort amsPort = AdsAbstractPlcConnection.generateAMSPort();
        assertNotNull(amsPort);
    }

    @Test
    public void close() throws Exception {
        Map addressMapping = (Map) FieldUtils.getDeclaredField(AdsAbstractPlcConnection.class, "addressMapping", true).get(SUT);
        addressMapping.put(mock(SymbolicAdsAddress.class), mock(AdsAddress.class));
        SUT.close();
    }

    @Test
    public void getFromFuture() throws Exception {
        runInThread(() -> {
            CompletableFuture completableFuture = mock(CompletableFuture.class, RETURNS_DEEP_STUBS);
            Object fromFuture = SUT.getFromFuture(completableFuture, 1);
            assertNotNull(fromFuture);
        });
        runInThread(() -> {
            CompletableFuture completableFuture = mock(CompletableFuture.class, RETURNS_DEEP_STUBS);
            when(completableFuture.get(anyLong(), any())).thenThrow(InterruptedException.class);
            assertThrows(PlcRuntimeException.class, () -> SUT.getFromFuture(completableFuture, 1));
        });
        runInThread(() -> {
            CompletableFuture completableFuture = mock(CompletableFuture.class, RETURNS_DEEP_STUBS);
            when(completableFuture.get(anyLong(), any())).thenThrow(ExecutionException.class);
            assertThrows(PlcRuntimeException.class, () -> SUT.getFromFuture(completableFuture, 1));
        });
        runInThread(() -> {
            CompletableFuture completableFuture = mock(CompletableFuture.class, RETURNS_DEEP_STUBS);
            when(completableFuture.get(anyLong(), any())).thenThrow(TimeoutException.class);
            assertThrows(PlcRuntimeException.class, () -> SUT.getFromFuture(completableFuture, 1));
        });
        assertFalse("The current Thread should not be interrupted", Thread.currentThread().isInterrupted());
    }

    /**
     * Runs tests steps in a dedicated {@link Thread} so a possible {@link InterruptedException} doesn't lead to a
     * interrupt flag being set on the main Thread ({@link Thread#isInterrupted}).
     *
     * @param testRunnable a special {@link Runnable} which adds a {@code throws Exception} to the {@code run} signature.
     * @throws InterruptedException when this {@link Thread} gets interrupted.
     */
    public void runInThread(TestRunnable testRunnable) throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                testRunnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Queue<Throwable> uncaughtExceptions = new ConcurrentLinkedQueue<>();
        thread.setUncaughtExceptionHandler((t, e) -> uncaughtExceptions.add(e));
        thread.start();
        thread.join();
        if (!uncaughtExceptions.isEmpty()) {
            uncaughtExceptions.forEach(throwable -> LOGGER.error("Assertion Error: Unexpected Exception", throwable));
            throw new AssertionError("Test failures. Check log");
        }
    }

    @Test
    public void testToString() {
        String s = SUT.toString();
        assertNotNull(s);
    }

    /**
     * Variant of {@link Runnable} which adds a {@code throws Exception} to the {@code run} signature.
     */
    private interface TestRunnable {
        /**
         * @throws Exception when the test throws a exception.
         * @see Runnable#run()
         */
        void run() throws Exception;
    }

    private static class DummyException extends Exception {

    }

    @FunctionalInterface
    private interface FutureProducingTestRunnable {
        CompletableFuture run() throws Exception;
    }
}