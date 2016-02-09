/*
 * Copyright 2015 Collective, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.collective.celos.ui;

import com.collective.celos.CelosClient;

import java.net.URISyntaxException;
import java.net.URL;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.collective.celos.JettyServer;
import com.collective.celos.Util;
import com.collective.celos.WorkflowID;

import javax.servlet.ServletContext;

/**
 * Main class that launches the Celos UI.
 */
public class Main {

    public static final String CELOS_URL_ATTR = "CELOS_URL";
    public static final String HUE_URL_ATTR = "HUE_URL";
    public static final String CONFIG_FILE_ATTR = "CONFIG_FILE";

    public static void main(String... args) throws Exception {
        UICommandLineParser UICommandLineParser = new UICommandLineParser();
        UICommandLine commandLine = UICommandLineParser.parse(args);
        JettyServer jettyServer = new JettyServer();
        jettyServer.start(commandLine.getPort());
        jettyServer.setupContext(getAttributes(commandLine), new HashMap<>());
    }

    private static Map<String, Object> getAttributes(UICommandLine commandLine) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(CELOS_URL_ATTR, commandLine.getCelosUrl());
        attrs.put(HUE_URL_ATTR, commandLine.getHueUrl());
        attrs.put(CONFIG_FILE_ATTR, commandLine.getConfigFile());
        return attrs;
    }
    
}
