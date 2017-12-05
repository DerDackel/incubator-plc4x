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
package org.apache.plc4x.java.s7;

import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.connection.PlcConnection;
import org.apache.plc4x.java.api.connection.PlcReader;
import org.apache.plc4x.java.api.model.Address;
import org.apache.plc4x.java.api.messages.BytePlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class S7PlcDriverSample {

    private final static Logger logger = LoggerFactory.getLogger(S7PlcDriverSample.class);

    /**
     * Example code do demonstrate using the S7 Plc Driver.
     *
     * @param args ignored.
     * @throws Exception something went wrong.
     */
    public static void main(String[] args) throws Exception {
        // Create a connection to the S7 PLC (s7://{hostname/ip}/{racknumber}/{slotnumber})
        logger.info("Connecting");
        try (PlcConnection plcConnection = new PlcDriverManager().getConnection("s7://192.168.0.1/0/0")){
            logger.info("Connected");

            Optional<PlcReader> reader = plcConnection.getReader();
            // Check if this connection support reading of data.
            if (reader.isPresent()) {
                PlcReader plcReader = reader.get();

                // Prepare some address object for accessing fields in the PLC.
                // ({memory-area}/{byte-offset}[/{bit-offset}]
                // "bit-offset is only specified if the requested type is "bit"
                // NOTICE: This format is probably only valid when using a S7 connection.
                Address inputs = plcConnection.parseAddress("INPUTS/0");
                Address outputs = plcConnection.parseAddress("OUTPUTS/0");

                //////////////////////////////////////////////////////////
                // Read synchronously ...
                // NOTICE: the ".get()" immediately lets this thread pause till
                // the response is processed and available.
                PlcReadResponse<Byte> plcReadResponse = plcReader.read(
                    new BytePlcReadRequest(inputs)).get();
                Byte data = plcReadResponse.getValue();
                System.out.println("Inputs: " + data);

                //////////////////////////////////////////////////////////
                // Read asynchronously ...
                Calendar start = Calendar.getInstance();
                CompletableFuture<PlcReadResponse<Byte>> asyncResponse = plcReader.read(
                    new BytePlcReadRequest(outputs));

                // Simulate doing something else ...
                System.out.println("Processing: ");
                while (true) {
                    // I had to make sleep this small or it would have printed only one "."
                    // On my system the average response time with a siemens s7-1200 was 5ms.
                    Thread.sleep(1);
                    System.out.print(".");
                    if (asyncResponse.isDone()) {
                        break;
                    }
                }
                System.out.println();

                Calendar end = Calendar.getInstance();
                plcReadResponse = asyncResponse.get();
                data = plcReadResponse.getValue();
                System.out.println("Outputs: " + data + " (in " + (end.getTimeInMillis() - start.getTimeInMillis()) + "ms)");
            }
        }
        // Catch any exception or the application won't be able to finish if something goes wrong.
        catch (Exception e) {
            e.printStackTrace();
        }
        // The application would cleanly terminate after several seconds ... this just speeds things up.
        System.exit(0);
    }

}