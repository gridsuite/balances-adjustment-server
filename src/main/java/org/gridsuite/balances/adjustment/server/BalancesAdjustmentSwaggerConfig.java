/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.balances.adjustment.server;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Configuration
public class BalancesAdjustmentSwaggerConfig {
    @Bean
    public OpenAPI createOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Balances adjustment API")
                .description("This is the documentation of balances adjustment REST API")
                .version(BalancesAdjustmentApi.API_VERSION));
    }
}
