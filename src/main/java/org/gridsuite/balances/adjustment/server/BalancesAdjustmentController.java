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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@Tag(name = "balances-adjustment-server")
@ComponentScan(basePackageClasses = BalancesAdjustmentService.class)
public class BalancesAdjustmentController {

    @Autowired
    private BalancesAdjustmentService balancesAdjustmentService;

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "run a balances adjustment on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The balances adjustment has been performed")})
    public ResponseEntity<BalanceComputationResult> computeBalancesAdjustment(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                                              @RequestParam(value = "balanceComputationParamsFile", required = false) MultipartFile balanceComputationParams,
                                                                              @RequestParam("targetNetPositionFile") MultipartFile targetNetPositionFile) throws ExecutionException, InterruptedException, IOException {
        BalanceComputationParameters parameters = balanceComputationParams != null
                ? JsonBalanceComputationParameters.read(balanceComputationParams.getInputStream())
                : null;

        InputStream targetNetPositionsStream = targetNetPositionFile != null ? targetNetPositionFile.getInputStream() : null;

        BalanceComputationResult result = balancesAdjustmentService.computeBalancesAdjustment(networkUuid, parameters, targetNetPositionsStream);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
