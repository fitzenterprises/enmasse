/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.bases;

import static io.enmasse.systemtest.apiclients.Predicates.any;
import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;

import io.enmasse.iot.model.v1.IoTConfig;
import io.enmasse.iot.model.v1.IoTProject;
import io.enmasse.systemtest.CustomLogger;
import io.enmasse.systemtest.TimeoutBudget;
import io.enmasse.systemtest.WaitPhase;
import io.enmasse.systemtest.apiclients.AddressApiClient;
import io.enmasse.systemtest.apiclients.UserApiClient;
import io.enmasse.systemtest.iot.HttpAdapterClient;
import io.enmasse.systemtest.iot.MessageType;
import io.enmasse.systemtest.timemeasuring.SystemtestsOperation;
import io.enmasse.systemtest.timemeasuring.TimeMeasuringSystem;
import io.enmasse.systemtest.utils.IoTUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

public abstract class IoTTestBase extends TestBase {

    protected static final String IOT_ADDRESS_EVENT = "event";
    protected static final String IOT_ADDRESS_TELEMETRY = "telemetry";
    protected static final String IOT_ADDRESS_CONTROL = "control";

    protected static Logger log = CustomLogger.getLogger();

    private List<IoTConfig> iotConfigs = new ArrayList<>();
    private List<IoTProject> iotProjects = new ArrayList<>();

    protected String iotProjectNamespace = "iot-project-ns";

    @BeforeEach
    public void setupIoT() throws Exception {
        if (!kubernetes.namespaceExists(iotProjectNamespace)) {
            kubernetes.createNamespace(iotProjectNamespace);
        }
        //additional clients that need to query against the namespace where iotprojects are created
        addressApiClient = new AddressApiClient(kubernetes, iotProjectNamespace);
        setUserApiClient(new UserApiClient(kubernetes, iotProjectNamespace));
    }

    @AfterEach
    public void teardownIoT() throws Exception {
        try {
            if (!environment.skipCleanup()) {
                //FIXME maybe collect logs of iot related pods?
                log.info("All IoTProjects will be removed");
                for (IoTProject project : iotProjects) {
                    var iotProjectApiClient = kubernetes.getIoTProjectClient(project.getMetadata().getNamespace());
                    if (iotProjectApiClient.withName(project.getMetadata().getName()).get() != null) {
                        IoTUtils.deleteIoTProjectAndWait(kubernetes, project, addressApiClient);
                    } else {
                        log.info("IoTProject '" + project.getMetadata().getName() + "' doesn't exists!");
                    }
                }
                iotProjects.clear();
                log.info("All IoTConfigs will be removed");
                var iotConfigApiClient = kubernetes.getIoTConfigClient();
                for (IoTConfig config : iotConfigs) {
                    if (iotConfigApiClient.withName(config.getMetadata().getName()).get() != null) {
                        iotConfigApiClient.delete(config);
                    } else {
                        log.info("IoTConfig '" + config.getMetadata().getName() + "' doesn't exists!");
                    }
                }
                iotConfigs.clear();
            } else {
                log.warn("Clean IoT environment in tear down - SKIPPED!");
            }
        } catch (Exception e) {
            log.error("Error tearing down iot test: {}", e.getMessage());
            throw e;
        }
    }

    public IoTConfig getSharedIoTConfig() {
        return null;
    }

    public IoTProject getSharedIoTProject() {
        return null;
    }

    /**
     * Get the Hono tenant name from the project configuration.
     */
    protected String tenantId() {
        var project = getSharedIoTProject();
        if (project == null) {
            return null;
        }
        return String.format("%s.%s", project.getMetadata().getNamespace(), project.getMetadata().getName());
    }

    protected void createIoTConfig(IoTConfig config) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.CREATE_IOT_CONFIG);
        var iotConfigApiClient = kubernetes.getIoTConfigClient();
        if (iotConfigApiClient.withName(config.getMetadata().getName()).get() != null) {
            log.info("iot config {} already exists", config.getMetadata().getName());
        } else {
            log.info("iot config {} will be created", config.getMetadata().getName());
            iotConfigApiClient.create(config);
            if (!config.equals(getSharedIoTConfig())) {
                iotConfigs.add(config);
            }
        }
        IoTUtils.waitForIoTConfigReady(kubernetes, config);
        IoTUtils.syncIoTConfig(kubernetes, config);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    protected void createIoTProject(IoTProject project) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.CREATE_IOT_PROJECT);
        var iotProjectApiClient = kubernetes.getIoTProjectClient(project.getMetadata().getNamespace());
        if (iotProjectApiClient.withName(project.getMetadata().getName()).get() != null) {
            log.info("iot project {} already exists", project.getMetadata().getName());
        } else {
            log.info("iot project {} will be created", project.getMetadata().getName());
            iotProjectApiClient.create(project);
            if (!project.equals(getSharedIoTProject())) {
                iotProjects.add(project);
            }
        }
        IoTUtils.waitForIoTProjectReady(kubernetes, addressApiClient, project);
        IoTUtils.syncIoTProject(kubernetes, project);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    protected void waitForFirstSuccessOnTelemetry(HttpAdapterClient adapterClient) throws Exception {
        waitForFirstSuccess(adapterClient, MessageType.TELEMETRY);
    }

    protected void waitForFirstSuccess(HttpAdapterClient adapterClient, MessageType type) throws Exception {
        JsonObject json = new JsonObject(Map.of("a", "b"));
        String message = "First successful "+type.name().toLowerCase()+" message";
        TestUtils.waitUntilCondition(message, (phase) -> {
            try {
                if(type == MessageType.EVENT) {
                    var response = adapterClient.sendEvent(json, any());
                    logResponseIfLastTryFailed(phase, response, message);
                    return response.statusCode() == HTTP_ACCEPTED;
                } else if(type == MessageType.TELEMETRY) {
                    var response = adapterClient.sendTelemetry(json, any());
                    logResponseIfLastTryFailed(phase, response, message);
                    return response.statusCode() == HTTP_ACCEPTED;
                } else {
                    return true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, new TimeoutBudget(3, TimeUnit.MINUTES));

        log.info("First "+type.name().toLowerCase()+" message accepted");
    }

    private void logResponseIfLastTryFailed(WaitPhase phase, HttpResponse<?> response, String warnMessage) {
        if(phase == WaitPhase.LAST_TRY && response.statusCode() != HTTP_ACCEPTED) {
            log.error("expected-code: {}, response-code: {}, body: {}, op: {}", HTTP_ACCEPTED, response.statusCode(), response.body(), warnMessage);
        }
    }

}
