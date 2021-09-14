/*
 * Copyright 2021 tareq.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javalin.plugin.primefaces;

import io.javalin.Javalin;
import io.javalin.core.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.faces.application.ProjectStage;
import javax.faces.webapp.FacesServlet;
import static org.apache.myfaces.ee.MyFacesContainerInitializer.FACES_SERVLET_ADDED_ATTRIBUTE;
import org.apache.myfaces.webapp.StartupServletContextListener;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 *
 * @author tareq
 */
public class JavalinPrimefacesPlugin implements Plugin {

    private final JavalinPrimefacesPluginConfig config;

    private JavalinPrimefacesPlugin() {
        this.config = new JavalinPrimefacesPluginConfig();
    }

    public static JavalinPrimefacesPlugin init(Consumer<JavalinPrimefacesPluginConfig> config) {
        JavalinPrimefacesPlugin plugin = new JavalinPrimefacesPlugin();
        config.accept(plugin.config);
        return plugin;
    }

    @Override
    public void apply(Javalin app) {
        app.events(event -> {
            event.serverStarting(() -> {
                Server server = app.jettyServer().server();
                WebAppContext webappContext = generateWebAppContext();
                config.parameters.entrySet().forEach(entry -> {
                    webappContext.setInitParameter(entry.getKey(), entry.getValue());
                });
                if (config.isDev()) {
                    webappContext.setInitParameter(ProjectStage.PROJECT_STAGE_PARAM_NAME, ProjectStage.Development.name());
                }
                webappContext.setAttribute("javalinPrimefaces", this);
                webappContext.setContextPath(config.primefacesPath());
                /**
                 * Need to declare that we added the Faces Servlet Dynamically
                 * instead of through a web.xml or faces-config.xml. This
                 * property, and how it works, can be found here
                 *
                 * https://github.com/apache/myfaces/blob/114fd7af5c42865c48ef0d98e185dd102e2b9395/impl/src/main/java/org/apache/myfaces/webapp/FacesInitializerImpl.java#L167
                 */
                webappContext.setAttribute(FACES_SERVLET_ADDED_ATTRIBUTE, Boolean.TRUE);
                ServletHolder jsfServlet = generateJsfServlet();
                webappContext.addServlet(jsfServlet, "*.xhtml");
                webappContext.setWelcomeFiles(new String[]{"index.xhtml"});
                ContextHandlerCollection handlers = new ContextHandlerCollection();
                handlers.setHandlers(new Handler[]{webappContext});
                server.setHandler(handlers);
                webappContext.addEventListener(new StartupServletContextListener());
            });
        });
    }

    /**
     * Generates the Web App Context and Aliases Classes so it works without
     * needing to move them into WEB-INF during builds, Additionally, sets the
     * resources dir for xhtml files, so that we can actually run them from
     * inside the jar. this Dir is actually copied from
     * src/main/resources/webapp in maven, but must be present in the "webapp"
     * folder in the classpath root or jar in order to work correctly
     *
     * @return a preconfigured webappcontext that lets us actually run embedded
     */
    private WebAppContext generateWebAppContext() {
        final String webappDir = config.webappPath();
        WebAppContext webappContext = new WebAppContext(webappDir, "/") {
            @Override
            public String getResourceAlias(String alias) {
                final Map<String, String> resourceAliases = (Map<String, String>) getResourceAliases();
                if (resourceAliases == null) {
                    return null;
                }
                for (Entry<String, String> oneAlias : resourceAliases.entrySet()) {

                    if (alias.startsWith(oneAlias.getKey())) {
                        return alias.replace(oneAlias.getKey(), oneAlias.getValue());
                    }
                }
                return null;
            }
        };
        /**
         * A Workaround during development on maven-based builds, so reflection
         * can actually work. See this repository on how this was figured out
         * https://github.com/slindenberg/primefaces-jetty
         */
        try {
            webappContext.setBaseResource(new ResourceCollection(new String[]{webappDir, "./target"}));
            webappContext.setResourceAlias("/WEB-INF/classes/", "/classes/");
        } catch (Exception e) {
        }
        return webappContext;
    }

    private ServletHolder generateJsfServlet() {
        ServletHolder jsfServlet = new ServletHolder(FacesServlet.class);
        jsfServlet.setDisplayName("Faces Servlet");
        jsfServlet.setName("Faces_Servlet");
        jsfServlet.setInitOrder(0);
        return jsfServlet;
    }

    public class JavalinPrimefacesPluginConfig {

        String primefacesPath = "/faces";
        Map<String, String> parameters = new HashMap<>();
        boolean isDev = false;
        String webappPath = this.getClass().getClassLoader().getResource("webapp").toExternalForm();

        /**
         * Sets the context path for primefaces
         *
         * @param path the path to set
         * @return current config
         */
        public JavalinPrimefacesPluginConfig primefacesPath(String path) {
            this.primefacesPath = path;
            return this;
        }

        /**
         * Sets a classpath path for the webapp root folder
         *
         * @param path the classpath path for the webapp root folder
         * @return current config
         */
        public JavalinPrimefacesPluginConfig webappClassPath(String path) {
            this.webappPath = this.getClass().getClassLoader().getResource(path).toExternalForm();
            return this;
        }

        /**
         * Sets a filesystem path for the webapp root folder
         *
         * @param path the path to the webapp folder
         * @return current config
         */
        public JavalinPrimefacesPluginConfig webappFolderPath(String path) {
            this.webappPath = path;
            return this;
        }

        /**
         * Sets whether the environment is dev or not. if it is dev, also
         * changes the webapp folder path to be src/main/resources/webapp
         *
         * @param isDev whether we are running in dev mode or not
         * @return current config
         */
        public JavalinPrimefacesPluginConfig isDev(boolean isDev) {
            this.isDev = isDev;
            if (isDev) {
                this.webappFolderPath("src/main/resources/webapp");
            }
            return this;
        }

        /**
         * Adds a Servlet Init parameter to be passed to the underlying server
         *
         * @param name the name of the parameter
         * @param value the value of the parameter
         * @return current config
         */
        public JavalinPrimefacesPluginConfig parameter(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        public boolean isDev() {
            return isDev;
        }

        public String webappPath() {
            return this.webappPath;
        }

        public String primefacesPath() {
            String normalizedPath = primefacesPath;
            normalizedPath = normalizedPath.replaceAll("/+", "/");
            if (normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            return normalizedPath;
        }

    }

}
