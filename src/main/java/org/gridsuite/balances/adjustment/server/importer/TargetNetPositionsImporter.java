/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.balances.adjustment.server.importer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class TargetNetPositionsImporter {

    private TargetNetPositionsImporter() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static Map<String, Double> getTargetNetPositionsAreasFromFile(InputStream input) throws IOException {

        Map<String, Double> netPositionAreas = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> jsonMap = mapper.readValue(input, Map.class);
        ArrayList jsonExchangeDataList = (ArrayList) jsonMap.get("netPositions");
        for (Object object : jsonExchangeDataList) {
            HashMap<String, Object> jsonObj = (HashMap<String, Object>) object;
            String area = (String) jsonObj.get("area");
            double netPosition = (double) jsonObj.get("netPosition");
            netPositionAreas.put(area, netPosition);
        }
        return netPositionAreas;
    }
}
