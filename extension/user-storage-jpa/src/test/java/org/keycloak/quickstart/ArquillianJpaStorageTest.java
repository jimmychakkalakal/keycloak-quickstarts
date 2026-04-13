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

package org.keycloak.quickstart;

import jakarta.ws.rs.core.Response;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.quickstart.test.page.LoginPage;
import org.keycloak.quickstart.storage.user.MyExampleUserStorageProviderFactory;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.util.JsonSerialization;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;

@ExtendWith(ArquillianExtension.class)
@KeycloakIntegrationTest(config = ArquillianJpaStorageTest.ServerConfig.class)
public class ArquillianJpaStorageTest {

    public static final String KEYCLOAK_URL = "http://localhost:8080";
    public static final String PROVIDER_TYPE = "org.keycloak.storage.UserStorageProvider";

    @InjectRealm(config = ArquillianJpaStorageTest.QuickstartRealmConfig.class)
    static ManagedRealm realm;

    @Page
    private LoginPage loginPage;

    @Drone
    private WebDriver webDriver;

    private static boolean realmConfigured = false;

    @BeforeEach
    public void beforeTest() {
        if (!realmConfigured) {
            RequiredActionProviderRepresentation ra = realm.admin().flows().getRequiredAction("VERIFY_PROFILE");
            ra.setEnabled(false);
            realm.admin().flows().updateRequiredAction("VERIFY_PROFILE", ra);
            realmConfigured = true;
        }
        webDriver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);
        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }

    private void navigateTo(String path) {
        webDriver.navigate().to(KEYCLOAK_URL + path);
    }

    @Test
    public void testCreateUserInStorage() {
        final String providerId = addProvider();
        final String username = "joneill";
        final String password = "sgc-passwd";

        createUser(username, password);
        // The MyUserStorageProvider doesn't implement all methods, e.g. searchForUserStream using attributes is missing, therefore we
        // need to use a different interface, in this case using "search" attribute and pagination
        UserRepresentation fetchedUser = realm.admin().users().search(username, 0, 1).get(0);

        // check if the user is created using the storage provider
        Assertions.assertEquals(providerId, fetchedUser.getOrigin());

        // test if login works
        navigateToAccount(username, password);
    }

    private void createUser(String username, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));
        realm.admin().users().create(user).close();
    }

    private String addProvider() {
        ComponentRepresentation provider = new ComponentRepresentation();
        provider.setProviderId(MyExampleUserStorageProviderFactory.PROVIDER_ID);
        provider.setProviderType(PROVIDER_TYPE);
        provider.setName(MyExampleUserStorageProviderFactory.PROVIDER_ID);

        Response response = realm.admin().components().add(provider);
        Assertions.assertEquals(201, response.getStatus());

        String location = response.getLocation().getPath();
        String id = location.substring(location.lastIndexOf('/') + 1);
        response.close();
        return id;
    }

    private void navigateToAccount(String user, String password) {
        navigateTo(format("/realms/%s/account/#/", realm.getName()));
        waitForPageToLoad();
        loginPage.login(user, password);
    }

    public void waitForPageToLoad() {
        // Taken from org.keycloak.testsuite.util.WaitUtils

        String currentUrl = null;

        // Ensure the URL is "stable", i.e. is not changing anymore; if it'd changing, some redirects are probably still in progress
        for (int maxRedirects = 4; maxRedirects > 0; maxRedirects--) {
            currentUrl = webDriver.getCurrentUrl();
            FluentWait<WebDriver> wait = new FluentWait<>(webDriver).withTimeout(Duration.ofMillis(250));
            try {
                wait.until(not(urlToBe(currentUrl)));
            }
            catch (TimeoutException e) {
                break; // URL has not changed recently - ok, the URL is stable and page is current
            }
        }
    }

    public static class ServerConfig implements KeycloakServerConfig {

        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return config.dependencyCurrentProject();
        }
    }

    static class QuickstartRealmConfig implements RealmConfig {

        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realmConfigBuilder) {
            // Load the realm from JSON and override it completely
            try (InputStream is = getClass().getResourceAsStream("/quickstart-realm.json")) {
                RealmRepresentation realmRep = JsonSerialization.readValue(is, RealmRepresentation.class);

                // Extract key properties from JSON
                return realmConfigBuilder
                        .name(realmRep.getRealm())
                        .sslRequired(realmRep.getSslRequired())
                        .ssoSessionIdleTimeout(realmRep.getSsoSessionIdleTimeout())
                        .ssoSessionMaxLifespan(realmRep.getSsoSessionMaxLifespan())
                        .registrationAllowed(realmRep.isRegistrationAllowed());
            } catch (IOException e) {
                throw new RuntimeException("Failed to load realm from JSON", e);
            }
        }
    }
}
