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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.UserModel;
import org.keycloak.quickstart.page.ConsolePage;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.quickstart.test.page.LoginPage;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.userprofile.config.UPConfigUtils;
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
import static org.keycloak.quickstart.util.StorageManager.addUser;
import static org.keycloak.quickstart.util.StorageManager.createStorage;
import static org.keycloak.quickstart.util.StorageManager.getPropertyFile;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;


@ExtendWith(ArquillianExtension.class)
@KeycloakIntegrationTest(config = ArquillianSimpleStorageTest.ServerConfig.class)
public class ArquillianSimpleStorageTest {

    public static final String KEYCLOAK_URL = "http://localhost:8080";

    @InjectRealm(config = ArquillianSimpleStorageTest.QuickstartRealmConfig.class)
    static ManagedRealm realm;

    @Page
    private LoginPage loginPage;

    @Page
    private ConsolePage consolePage;

    @Drone
    private WebDriver webDriver;

    private static boolean realmConfigured = false;

    @BeforeEach
    public void beforeTest() {
        if (!realmConfigured) {
            configureRealm();
            createUsers();
            realmConfigured = true;
        }
        webDriver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);
        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }

    private void configureRealm() {
        RequiredActionProviderRepresentation ra = realm.admin().flows().getRequiredAction("VERIFY_PROFILE");
        ra.setEnabled(false);
        realm.admin().flows().updateRequiredAction("VERIFY_PROFILE", ra);
        disableUserProfileAttributes();
    }

    private void createUsers() {
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

    // Disable email, firstName, lastName attributes from user-profile
    private void disableUserProfileAttributes() {
        UPConfig upConfig = realm.admin().users().userProfile().getConfiguration();

        removeUserPermissionsFromAttribute(upConfig, UserModel.EMAIL);
        removeUserPermissionsFromAttribute(upConfig, UserModel.FIRST_NAME);
        removeUserPermissionsFromAttribute(upConfig, UserModel.LAST_NAME);

        realm.admin().users().userProfile().update(upConfig);
    }

    private void removeUserPermissionsFromAttribute(UPConfig upConfig, String attrName) {
        UPAttributePermissions upAttributePermissions = upConfig.getAttribute(attrName).getPermissions();
        upAttributePermissions.getEdit().remove(UPConfigUtils.ROLE_USER);
        upAttributePermissions.getView().remove(UPConfigUtils.ROLE_USER);
    }

    private void navigateTo(String path) {
        webDriver.navigate().to(KEYCLOAK_URL + path);
    }

    @Test
    public void testUserReadOnlyFederationStorage() {
        addProvider(org.keycloak.quickstart.readonly.PropertyFileUserStorageProviderFactory.PROVIDER_NAME);
        Assertions.assertEquals(0, realm.admin().users().search("tbrady").size(), "There should be no tbrady user");

        navigateToAccount("tbrady", "superbowl", false);
        Assertions.assertEquals("tbrady", consolePage.getUser(), "Should display the user from storage provider");
        consolePage.logout();
    }

    @Test
    public void testUserWritableFederationStorage() {
        Assertions.assertEquals(2, (long) realm.admin().users().count(), "There should be two users");
        Assertions.assertEquals(2, realm.admin().users().list().size(), "There should be two users listed");
        Assertions.assertEquals(0, realm.admin().users().search("malcom").size(), "There should be no malcom user");
        Assertions.assertEquals(0, realm.admin().users().search("rob").size(), "There should be no rob user");

        createStorage();
        addUser("malcom", "butler");
        addProvider(org.keycloak.quickstart.writeable.PropertyFileUserStorageProviderFactory.PROVIDER_NAME);

        navigateToAccount("malcom", "butler", false);
        Assertions.assertEquals("malcom", consolePage.getUser(), "Should display the user from storage provider");
        consolePage.logout();

        addUser("rob", "gronkowski");
        navigateToAccount("rob", "gronkowski", false);
        Assertions.assertEquals("rob", consolePage.getUser(), "Should display the user from storage provider");
        consolePage.logout();

        Assertions.assertEquals(4, (long) realm.admin().users().count(), "There should be four users");
        Assertions.assertEquals(4, realm.admin().users().list().size(), "There should be four users listed");

        List<UserRepresentation> list = realm.admin().users().list(2, 2);
        Assertions.assertEquals(2, list.size(), "There should be two users listed");
        Assertions.assertEquals("malcom", list.get(0).getUsername(), "First user should be malcom");
        Assertions.assertEquals("rob", list.get(1).getUsername(), "Second user should be rob");
    }

    private void addProvider(String providerId) {
        ComponentRepresentation provider = new ComponentRepresentation();
        provider.setProviderId(providerId);
        provider.setProviderType("org.keycloak.storage.UserStorageProvider");
        provider.setName(providerId);

        if (org.keycloak.quickstart.writeable.PropertyFileUserStorageProviderFactory.PROVIDER_NAME.equals(providerId)) {
            provider.setConfig(new MultivaluedHashMap<String, String>() {{
                putSingle("path", getPropertyFile());
            }});
        }

        Response response = realm.admin().components().add(provider);
        Assertions.assertEquals(201, response.getStatus());
    }

    private void navigateToAccount(String user, String password, boolean changePassword) {
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
