/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.balances.adjustment.server;

import com.powsybl.action.util.Scalable;
import com.powsybl.balances_adjustment.balance_computation.*;
import com.powsybl.balances_adjustment.util.CountryAreaFactory;
import com.powsybl.balances_adjustment.util.NetworkAreaFactory;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManagerFactory;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.balances.adjustment.server.importer.TargetNetPositionsImporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
public class BalancesAdjustmentService {

    @Autowired
    private NetworkStoreService networkStoreService;

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    BalanceComputationResult computeBalancesAdjustment(UUID networkUuid, BalanceComputationParameters parameters, InputStream targetNetPositionsStream) throws ExecutionException, InterruptedException, IOException {
        Network network = getNetwork(networkUuid);

        BalanceComputationParameters params = parameters != null ? parameters : new BalanceComputationParameters();
        Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);

        BalanceComputationFactory balanceComputationFactory = new BalanceComputationFactoryImpl();
        List<BalanceComputationArea> computationAreas = createBalanceComputationAreas(network, targetNetPositions);
        BalanceComputation balanceComputation = balanceComputationFactory.create(computationAreas, LoadFlow.find(), new LocalComputationManagerFactory().create());
        // launch the balances adjustment on the network
        CompletableFuture<BalanceComputationResult> result = balanceComputation.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, params);
        return result.get();
    }

    List<BalanceComputationArea> createBalanceComputationAreas(Network network, Map<String, Double> targetNetPositions) {
        Map<String, NetworkAreaFactory> networkAreas = network.getCountries().stream()
                .collect(Collectors.toMap(Country::toString, CountryAreaFactory::new));

        return network.getCountries().stream()
                .map(country -> createBalanceComputationArea(network, country, networkAreas, targetNetPositions))
                .collect(Collectors.toList());
    }

    private BalanceComputationArea createBalanceComputationArea(Network network, Country country, Map<String, NetworkAreaFactory> networkAreas, Map<String, Double> targetNetPositions) {
        String countryName = country.getName();
        String countryCode = country.toString();
        NetworkAreaFactory networkArea = networkAreas.get(countryCode);
        double targetNetPosition = targetNetPositions.get(countryCode);
        List<Generator> countryGenerators = network.getGeneratorStream().filter(g -> g.getRegulatingTerminal().getVoltageLevel().getSubstation().getCountry().equals(country.getName())).collect(Collectors.toList());
        double countryGeneratorsTotalP = 0d;
        for (Generator g : countryGenerators) {
            countryGeneratorsTotalP += g.getTargetP();
        }
        List<Float> percentages = new ArrayList<>();
        List<Scalable> scalables = new ArrayList<>();
        for (Generator g : countryGenerators) {
            percentages.add((float) (g.getTargetP() / countryGeneratorsTotalP * 100));
            scalables.add(Scalable.onGenerator(g.getId()));
        }
        return new BalanceComputationArea(countryName, networkArea, Scalable.proportional(percentages, scalables), targetNetPosition);
    }
}
