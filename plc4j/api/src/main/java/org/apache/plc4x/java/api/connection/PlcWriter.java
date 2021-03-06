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
package org.apache.plc4x.java.api.connection;


import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcWriteRequest;
import org.apache.plc4x.java.api.messages.specific.TypeSafePlcWriteResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Interface implemented by all PlcConnections that are able to write to remote resources.
 */
@FunctionalInterface
public interface PlcWriter {

    /**
     * Writes a given value to a PLC.
     *
     * @param writeRequest object describing the type, location and value that whould be written.
     * @return a {@link CompletableFuture} giving async access to the response of the write operation.
     */
    CompletableFuture<? extends PlcWriteResponse> write(PlcWriteRequest writeRequest);

    /**
     * Writes a given value to a PLC.
     *
     * @param writeRequest object describing the type, location and value that whould be written.
     * @param <T>          type that is being requested.
     * @return a {@link CompletableFuture} giving async access to the response of the write operation.
     */
    default <T> CompletableFuture<TypeSafePlcWriteResponse<T>> write(TypeSafePlcWriteRequest<T> writeRequest) {
        Objects.requireNonNull(writeRequest, "write request must not be null");
        return write((PlcWriteRequest) writeRequest).thenApply(TypeSafePlcWriteResponse::of);
    }

}
