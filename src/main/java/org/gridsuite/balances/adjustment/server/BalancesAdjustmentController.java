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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + BalancesAdjustmentApi.API_VERSION + "/")
@Api(tags = "balances-adjustment-server")
@ComponentScan(basePackageClasses = BalancesAdjustmentService.class)
public class BalancesAdjustmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalancesAdjustmentController.class);

    @Inject
    private BalancesAdjustmentService balancesAdjustmentService;

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value = "run a balances adjustment on a network", produces = APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The balances adjustment has been performed")})
    public ResponseEntity<BalanceComputationResult> computeBalancesAdjustment(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                                              @RequestParam("targetNetPositionFile") MultipartFile targetNetPositionFile,
                                                                              @RequestBody(required = false) String balanceComputationParams) throws ExecutionException, InterruptedException, IOException {
        BalanceComputationParameters parameters = balanceComputationParams != null
                ? JsonBalanceComputationParameters.read(new ByteArrayInputStream(balanceComputationParams.getBytes()))
                : null;

        InputStream targetNetPositionsStream = targetNetPositionFile != null ? targetNetPositionFile.getInputStream() : null;

        BalanceComputationResult result = balancesAdjustmentService.computeBalancesAdjustment(networkUuid, parameters, targetNetPositionsStream, false);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
