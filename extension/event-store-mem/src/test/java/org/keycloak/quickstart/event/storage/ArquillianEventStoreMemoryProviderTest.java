/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quickstart.event.storage;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.keycloak.events.EventType;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.quickstart.test.page.LoginPage;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
@ExtendWith(ArquillianExtension.class)
@KeycloakIntegrationTest(config = ArquillianEventStoreMemoryProviderTest.ServerConfig.class)
public class ArquillianEventStoreMemoryProviderTest {

    public static final String REALM_QS_EVENT_STORE = "event-store-mem";

    public static final String KEYCLOAK_URL = "http://localhost:8080";

    public static final String KEYCLOAK_URL_CONSOLE = KEYCLOAK_URL + "/admin/%s/console/#%s";

    private static String adminId;

    @InjectRealm(config = ArquillianEventStoreMemoryProviderTest.EventStoreRealmConfig.class)
    static ManagedRealm realm;

    @Page
    private LoginPage loginPage;

    @Drone
    private WebDriver webDriver;

    @BeforeEach
    public void init() {
        webDriver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        if (adminId == null) {
            createTestUsers();
            adminId = realm.admin().users().search("test-admin").get(0).getId();
        }

        enableEventsSettings();
    }

    private void createTestUsers() {
        // Create alice user
        UserRepresentation alice = new UserRepresentation();
        alice.setUsername("alice");
        alice.setEmail("alice@keycloak.org");
        alice.setFirstName("Alice");
        alice.setLastName("Liddel");
        alice.setEnabled(true);
        CredentialRepresentation aliceCred = new CredentialRepresentation();
        aliceCred.setType(CredentialRepresentation.PASSWORD);
        aliceCred.setValue("password");
        aliceCred.setTemporary(false);
        alice.setCredentials(List.of(aliceCred));
        realm.admin().users().create(alice).close();

        // Create test-admin user
        UserRepresentation testAdmin = new UserRepresentation();
        testAdmin.setUsername("test-admin");
        testAdmin.setEmail("test@admin.org");
        testAdmin.setFirstName("Admin");
        testAdmin.setLastName("Test");
        testAdmin.setEnabled(true);
        CredentialRepresentation adminCred = new CredentialRepresentation();
        adminCred.setType(CredentialRepresentation.PASSWORD);
        adminCred.setValue("password");
        adminCred.setTemporary(false);
        testAdmin.setCredentials(List.of(adminCred));
        realm.admin().users().create(testAdmin).close();
    }

    @Test
    public void testIfEventsAreShowed() throws InterruptedException {
        // Clear events
        clearEvents();

        // logout and login to generate some events
        logout();
        loginToAdminConsole();

        checkIfEventExists(EventType.CODE_TO_TOKEN.name(), EventType.LOGIN.name());

        // add an user to generate admin event
        addUser();
        checkAdminEvents("CREATE", "USER");

        // clear events and check if the events are gone (they should because they shouldn't be persisted in the DB)
        clearEvents();
        Assertions.assertTrue(realm.admin().getEvents().isEmpty());
        Assertions.assertTrue(realm.admin().getAdminEvents().isEmpty());
    }

    @AfterEach
    public void cleanup() {
        disableEventsSettings();
    }

    private void navigateToAdminConsole(String path) {
        webDriver.navigate().to(format(KEYCLOAK_URL_CONSOLE, REALM_QS_EVENT_STORE, path));
    }

    private void enableEventsSettings() {
        RealmEventsConfigRepresentation realmEventsConfig = realm.admin().getRealmEventsConfig();
        realmEventsConfig.setEventsEnabled(true);
        realmEventsConfig.setAdminEventsEnabled(true);
        realm.admin().updateRealmEventsConfig(realmEventsConfig);

        // Don't clear events on initial setup - realm is fresh and provider may not be fully initialized
    }

    private void clearEvents() {
        realm.admin().clearEvents();
        realm.admin().clearAdminEvents();
    }

    private void disableEventsSettings() {
        RealmEventsConfigRepresentation realmEventsConfig = realm.admin().getRealmEventsConfig();
        realmEventsConfig.setEventsEnabled(false);
        realmEventsConfig.setAdminEventsEnabled(false);
        realm.admin().updateRealmEventsConfig(realmEventsConfig);
    }

    private void loginToAdminConsole() throws InterruptedException {
        final String path = "/realms/" + REALM_QS_EVENT_STORE + "/clients";

        navigateToAdminConsole(path);

        loginPage.login("test-admin", "password");

        // wait for URL to stop changing
        while (true) {
            String previousUrl = webDriver.getCurrentUrl();
            TimeUnit.SECONDS.sleep(1);
            if (webDriver.getCurrentUrl().equals(previousUrl)) {
                break;
            }
        }
    }

    private void logout() {
        realm.admin().users().get(adminId).logout();
    }

    private void checkIfEventExists(String... events) {
        List<String> actualEvents = realm.admin().getEvents().stream().map(e -> e.getType()).collect(Collectors.toList());

        Assertions.assertTrue(actualEvents.containsAll(List.of(events)),
                "Expected events: " + List.of(events) + " but got: " + actualEvents);
    }

    private void checkAdminEvents(String operationType, String resourceType) {
        List<AdminEventRepresentation> adminEvents = realm.admin().getAdminEvents();
        List<String> operationTypes = adminEvents.stream().map(e -> e.getOperationType()).collect(Collectors.toList());
        List<String> resourceTypes = adminEvents.stream().map(e -> e.getResourceType()).collect(Collectors.toList());

        Assertions.assertTrue(operationTypes.contains(operationType),
                "Expected operation type: " + operationType + " but got: " + operationTypes);
        Assertions.assertTrue(resourceTypes.contains(resourceType),
                "Expected resource type: " + resourceType + " but got: " + resourceTypes);
    }

    private void addUser() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("test-user");
        realm.admin().users().create(user);
    }

    public static class ServerConfig implements KeycloakServerConfig {

        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return config
                    .dependencyCurrentProject()
                    .option("spi-eventsStore-provider", "in-mem");
        }
    }

    static class EventStoreRealmConfig implements RealmConfig {

        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realmConfigBuilder) {
            return realmConfigBuilder
                    .name("event-store-mem")
                    .sslRequired("external")
                    .ssoSessionIdleTimeout(600)
                    .ssoSessionMaxLifespan(36000)
                    .registrationAllowed(false);
        }
    }
}
