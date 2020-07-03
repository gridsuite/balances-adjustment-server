/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.balances.adjustment.server.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class TargetNetPositionsImporter {

    private TargetNetPositionsImporter() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static Map<String, Double> getTargetNetPositionsAreasFromFile(InputStream input) throws IOException {

        Map<String, Double> netPositionAreas = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonTree = mapper.readTree(input);
        if (jsonTree.hasNonNull("netPositions")) {
            JsonNode jsonExchangeData = jsonTree.get("netPositions");
            for (Iterator<JsonNode> it = jsonExchangeData.elements(); it.hasNext(); ) {
                JsonNode areaElement = it.next();
                if (areaElement.hasNonNull("area") && areaElement.hasNonNull("netPosition")) {
                    String area = areaElement.get("area").asText();
                    double netPosition = areaElement.get("netPosition").asDouble();
                    netPositionAreas.put(area, netPosition);
                }
            }
        }
        return netPositionAreas;
    }
}
