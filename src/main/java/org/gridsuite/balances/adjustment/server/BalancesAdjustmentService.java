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
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.balances.adjustment.server.importer.TargetNetPositionsImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
public class BalancesAdjustmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalancesAdjustmentService.class);
    private static final double MAXIMUM_NET_POSITION_MISMATCH_BEFORE_REDISPATCH = 1.;

    @Autowired
    private NetworkStoreService networkStoreService;

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    private void fixMinP(Network network, List<BalanceComputationArea> areas) {
        // For all generators in the balance computation areas, we set the minP to targetP when targetP < minP
        areas.forEach(area -> {
            List<Injection> injections = area.getScalable().filterInjections(network);
            injections.stream().filter(injection -> injection instanceof Generator)
                    .map(injection -> (Generator) injection)
                    .forEach(generator -> {
                        if (generator.getTargetP() < generator.getMinP()) {
                            generator.setMinP(generator.getTargetP());
                        }
                    });
        });
    }

    private void integrateCompensation(Network network) {
        // For all generators in the network, we set the targetP to -P
        network.getGenerators().forEach(generator -> {
            if (!Double.isNaN(generator.getTerminal().getP())) {
                generator.setTargetP(-generator.getTerminal().getP());
            }
        });
    }

    public BalanceComputationResult computeBalancesAdjustment(UUID networkUuid, BalanceComputationParameters parameters,
                                                              InputStream targetNetPositionsStream)  throws ExecutionException, InterruptedException, IOException {
        return computeBalancesAdjustment(networkUuid, parameters, targetNetPositionsStream, true, true);
    }

    public BalanceComputationResult computeBalancesAdjustment(UUID networkUuid, BalanceComputationParameters parameters,
                                                              InputStream targetNetPositionsStream,
                                                              boolean iterative,
                                                              boolean correctNetPositionsInconsistencies) throws ExecutionException, InterruptedException, IOException {
        Network network = getNetwork(networkUuid);

        BalanceComputationParameters params = parameters != null ? parameters : new BalanceComputationParameters();
        Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);

        BalanceComputationFactory balanceComputationFactory = new BalanceComputationFactoryImpl();
        List<BalanceComputationArea> computationAreas = createBalanceComputationAreas(network, targetNetPositions, iterative, correctNetPositionsInconsistencies);
        BalanceComputation balanceComputation = balanceComputationFactory.create(computationAreas, LoadFlow.find(), new LocalComputationManagerFactory().create());

        fixMinP(network, computationAreas);
        integrateCompensation(network);

        // launch the balances adjustment on the network
        CompletableFuture<BalanceComputationResult> futureResult = balanceComputation.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, params);

        BalanceComputationResult result = futureResult.get();
        if (result.getStatus() == BalanceComputationResult.Status.SUCCESS) {
            networkStoreService.flush(network);
        }
        return result;
    }

    private void dispatchNetPositionsInconsistencies(Network network, Map<String, NetworkAreaFactory> networkAreas,
                                                     Map<String, Double> targetNetPositions) {
        double initialNetPositionSum = networkAreas.values().stream().mapToDouble(networkArea -> networkArea.create(network).getNetPosition()).sum();
        double targetNetPositionSum = networkAreas.keySet().stream().mapToDouble(targetNetPositions::get).sum();

        if (Math.abs(initialNetPositionSum - targetNetPositionSum) < MAXIMUM_NET_POSITION_MISMATCH_BEFORE_REDISPATCH) {
            return;
        }
        LOGGER.warn("Important mismatch between initial total net positions ({}) and targeted ones ({}). a redispatch will occur on each area", initialNetPositionSum, targetNetPositionSum);

        double absTargetNetPositionSum = networkAreas.keySet().stream().mapToDouble(country -> Math.abs(targetNetPositions.get(country))).sum();
        networkAreas.forEach((countryKey, value) -> {
            double initialTarget = targetNetPositions.get(countryKey);
            double mismatch = initialNetPositionSum - targetNetPositionSum;
            double finalTarget = initialTarget + mismatch * Math.abs(initialTarget) / absTargetNetPositionSum;
            LOGGER.warn("Country {} target net position modified from {} to {}", countryKey, initialTarget, finalTarget);
            targetNetPositions.put(countryKey, finalTarget);
        });
    }

    private void completeInputMaps(Network network, Map<String, NetworkAreaFactory> networkAreas,
                                   Map<String, Double> targetNetPositions) {
        network.getCountries().forEach(country -> {
            String countryCode = country.toString();
            NetworkAreaFactory countryArea = networkAreas.get(countryCode);
            targetNetPositions.computeIfAbsent(countryCode, s -> countryArea.create(network).getNetPosition());
        });
    }

    public List<BalanceComputationArea> createBalanceComputationAreas(Network network, Map<String, Double> targetNetPositions,
                                                                      boolean iterative, boolean correctNetPositionsInconsistencies) {
        Map<String, NetworkAreaFactory> networkAreas = network.getCountries().stream()
                .collect(Collectors.toMap(Country::toString, CountryAreaFactory::new));

        completeInputMaps(network, networkAreas, targetNetPositions);
        if (correctNetPositionsInconsistencies) {
            dispatchNetPositionsInconsistencies(network, networkAreas, targetNetPositions);
        }

        return network.getCountries().stream()
                .map(country -> createBalanceComputationArea(network, country, networkAreas, targetNetPositions, iterative))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BalanceComputationArea createBalanceComputationArea(Network network, Country country, Map<String, NetworkAreaFactory> networkAreas,
                                                                Map<String, Double> targetNetPositions, boolean iterative) {
        String countryName = country.getName();
        String countryCode = country.toString();
        NetworkAreaFactory networkArea = networkAreas.get(countryCode);
        Double targetNetPosition = targetNetPositions.get(countryCode);
        List<Generator> countryGenerators = network.getGeneratorStream().filter(g -> country.getName().equals(g.getTerminal().getVoltageLevel().getSubstation().getCountry().get().getName())).collect(Collectors.toList());
        double countryGeneratorsTotalP = 0d;
        for (Generator g : countryGenerators) {
            countryGeneratorsTotalP += g.getTargetP();
        }
        List<Float> percentages = new ArrayList<>();
        List<Scalable> scalables = new ArrayList<>();
        LOGGER.debug("Size of generators list: {} for country {}", countryGenerators.size(), countryName);
        for (Generator g : countryGenerators) {
            float percent;
            if (countryGeneratorsTotalP != 0) {
                percent = (float) (g.getTargetP() / countryGeneratorsTotalP * 100);
            } else {
                percent = 100f / countryGenerators.size();
            }
            percentages.add(percent);
            scalables.add(Scalable.onGenerator(g.getId()));
            LOGGER.debug("Addition of percentage {} for generator {}", percent, g.getId());
        }
        return !countryGenerators.isEmpty() && targetNetPosition != null ? new BalanceComputationArea(countryName, networkArea, Scalable.proportional(percentages, scalables, iterative), targetNetPosition) : null;
    }
}
