/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.balances.adjustment.server;

import com.powsybl.balances_adjustment.balance_computation.BalanceComputationParameters;
import com.powsybl.balances_adjustment.balance_computation.BalanceComputationResult;
import com.powsybl.balances_adjustment.balance_computation.json_parameters.JsonBalanceComputationParameters;
import io.swagger.annotations.*;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + BalancesAdjustmentApi.API_VERSION + "/")
@Api(tags = "balances-adjustment-server")
@ComponentScan(basePackageClasses = BalancesAdjustmentService.class)
public class BalancesAdjustmentController {

    @Inject
    private BalancesAdjustmentService balancesAdjustmentService;

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value = "run a balances adjustment on a network", produces = APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The balances adjustment has been performed")})
    public ResponseEntity<BalanceComputationResult> computeBalancesAdjustment(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                                              @ApiParam(value = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks,
                                                                              @RequestParam(value = "balanceComputationParamsFile", required = false) MultipartFile balanceComputationParams,
                                                                              @RequestParam("targetNetPositionFile") MultipartFile targetNetPositionFile) throws ExecutionException, InterruptedException, IOException {
        BalanceComputationParameters parameters = balanceComputationParams != null
                ? JsonBalanceComputationParameters.read(balanceComputationParams.getInputStream())
                : null;

        InputStream targetNetPositionsStream = targetNetPositionFile != null ? targetNetPositionFile.getInputStream() : null;

        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();

        BalanceComputationResult result = balancesAdjustmentService.computeBalancesAdjustment(networkUuid, otherNetworksUuid, parameters, targetNetPositionsStream);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
