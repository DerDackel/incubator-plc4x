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
package org.apache.plc4x.java.examples.kafkabridge;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.edgent.connectors.kafka.KafkaProducer;
import org.apache.edgent.function.Supplier;
import org.apache.edgent.providers.direct.DirectProvider;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.Topology;
import org.apache.plc4x.edgent.PlcConnectionAdapter;
import org.apache.plc4x.edgent.PlcFunctions;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.items.ReadRequestItem;
import org.apache.plc4x.java.api.messages.items.ReadResponseItem;
import org.apache.plc4x.java.api.model.Address;
import org.apache.plc4x.java.examples.kafkabridge.model.PlcAddress;
import org.apache.plc4x.java.examples.kafkabridge.model.Configuration;
import org.apache.plc4x.java.examples.kafkabridge.model.PlcMemoryBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBridge {

    private static final Logger logger = LoggerFactory.getLogger(KafkaBridge.class);

    private Configuration config;
    private PlcConnectionAdapter plcAdapter;

    private KafkaBridge(String propsPath) {
        if(StringUtils.isEmpty(propsPath)) {
            logger.error("Empty configuration file parameter");
            throw new IllegalArgumentException("Empty configuration file parameter");
        }
        File propsFile = new File(propsPath);
        if(!(propsFile.exists() && propsFile.isFile())) {
            logger.error("Invalid configuration file {}", propsFile.getPath());
            throw new IllegalArgumentException("Invalid configuration file " + propsFile.getPath());
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            config = mapper.readValue(propsFile, Configuration.class);
            plcAdapter = new PlcConnectionAdapter(config.getPlcConfig().getConnection());
        } catch (IOException e) {
            logger.error("Error parsing configuration", e);
        }
    }

    private void run() throws PlcException {
        DirectProvider dp = new DirectProvider();
        Topology top = dp.newTopology("kafka-bridge");

        // Build the entire request.
        Map<ReadRequestItem, String> names = new HashMap<>();
        PlcReadRequest readRequest = new PlcReadRequest();
        for(PlcMemoryBlock plcMemoryBlock : config.getPlcConfig().getPlcMemoryBlocks()) {
            for (PlcAddress address : config.getPlcConfig().getPlcAddresses()) {
                try {
                    Address plcAddress = plcAdapter.parseAddress(
                            "DATA_BLOCKS/" + plcMemoryBlock.getAddress() + "/" + address.getAddress());
                    ReadRequestItem readItem = new ReadRequestItem<>(address.getType(), plcAddress,
                            +address.getSize());
                    readRequest.addItem(readItem);
                    names.put(readItem, plcMemoryBlock.getName() + "/" + address.getName());
                } catch (PlcConnectionException e) {
                    logger.error("Error connecting to remote", e);
                    throw e;
                } catch (PlcException e) {
                    logger.error("Error parsing address {}", address.getAddress(), e);
                    throw e;
                }
            }
        }

        // Create a supplier that is able to read the batch we just created.
        Supplier<PlcReadResponse> plcSupplier = PlcFunctions.batchSupplier(plcAdapter, readRequest);

        // Start polling our plc source in the given interval.
        TStream<PlcReadResponse> source = top.poll(plcSupplier, config.getPollingInterval(), TimeUnit.MILLISECONDS);

        // Convert the byte into a string.
        TStream<String> jsonSource = source.map(value -> {
            JsonObject jsonObject = new JsonObject();
            for (ReadResponseItem<?> readResponseItem : value.getResponseItems()) {
                String name = names.get(readResponseItem.getRequestItem());
                if(readResponseItem.getValues().size() == 1) {
                    jsonObject.addProperty(name, Byte.toString((Byte) readResponseItem.getValues().get(0)));
                } else if (readResponseItem.getValues().size() > 1) {
                    JsonArray values = new JsonArray();
                    for (Object valueElement : readResponseItem.getValues()) {
                        values.add((Byte) valueElement);
                    }
                    jsonObject.add(name, values);
                }
            }
            return jsonObject.toString();
        });

        // Publish the stream to the topic. The String tuple is the message value.
        // Create the Kafka Producer broker connector
        Map<String,Object> kafkaConfig = createKafkaConfig();
        KafkaProducer kafka = new KafkaProducer(top, () -> kafkaConfig);
        kafka.publish(jsonSource, config.getKafkaConfig().getTopicName());

        dp.submit(top);
    }

    private Map<String,Object> createKafkaConfig() {
        Map<String,Object> kafkaConfig = new HashMap<>();
        kafkaConfig.put("bootstrap.servers", config.getKafkaConfig().getBootstrapServers());
        if(config.getKafkaConfig().getProperties() != null) {
            kafkaConfig.putAll(config.getKafkaConfig().getProperties());
        }
        return kafkaConfig;
    }

    public static void main(String[] args) throws Exception {
        if(args.length != 1) {
            System.out.println("Usage: KafkaBridge {path-to-kafka-bridge.yml}");
        }
        KafkaBridge kafkaBridge = new KafkaBridge(args[0]);
        kafkaBridge.run();
    }

}
