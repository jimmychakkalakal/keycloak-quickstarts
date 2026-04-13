/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
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

package org.keycloak.quickstart;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.quickstart.test.page.LoginPage;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;

/**
 */
@ExtendWith(ArquillianExtension.class)
@KeycloakIntegrationTest(config = ExtendAdminConsoleTest.ServerConfig.class)
public class ExtendAdminConsoleTest {

    public static final String KEYCLOAK_URL = "http://localhost:8080";

    @InjectRealm(config = ExtendAdminConsoleTest.MasterRealmConfig.class)
    static ManagedRealm realm;

    @Page
    private LoginPage loginPage;

    @Page
    private ExtendedAdminPage adminConsole;

    @Page
    private RealmSettingsAttributePage realmSettingsAttributePage;

    @Drone
    private WebDriver webDriver;

    @BeforeEach
    public void setup() {
        webDriver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }

    @Test
    public void testAdminUiTodoApp() {
        adminConsole.navigateTo();
        waitForPageToLoad();
        loginPage.login("admin", "admin");
        waitForPageToLoad();
        assertThat(webDriver.getTitle(), containsString("Keycloak Administration Console"));

        Assertions.assertTrue(adminConsole.isTodoMenuPresent());
        adminConsole.clickTodoMenuItem();
        waitForPageToLoad();
        Assertions.assertTrue(adminConsole.isOverviewPage());

        adminConsole.clickAddButton();
        waitForPageToLoad();
        adminConsole.fillTodoForm("something", "something that needs doing");
        adminConsole.clickSave();

        Assertions.assertTrue(adminConsole.isSaved());
    }

    @Test
    public void testRealmSettingsAttributes() {
        realmSettingsAttributePage.navigateTo();
        waitForPageToLoad();

        Assertions.assertTrue(realmSettingsAttributePage.logoInputExists());

        realmSettingsAttributePage.saveLogoField("http://assests.mycompany.com/logo.png");
        Assertions.assertTrue(realmSettingsAttributePage.isSaved());
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

    static class MasterRealmConfig implements RealmConfig {

        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realmConfigBuilder) {
            return realmConfigBuilder.name("master");
        }
    }
}
