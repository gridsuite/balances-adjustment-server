package org.gridsuite.balances.adjustment.server;

import com.powsybl.action.util.Scalable;
import com.powsybl.balances_adjustment.balance_computation.BalanceComputationArea;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.balances.adjustment.server.importer.TargetNetPositionsImporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@WebMvcTest(BalancesAdjustmentController.class)
@ContextConfiguration(classes = {BalancesAdjustmentApplication.class})
public class BalancesAdjustmentTest {

    private Network testNetwork;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Before
    public void setUp() {
        testNetwork = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testNetworkComputationAreasCreation() {
        BalancesAdjustmentService balancesAdjustmentService = new BalancesAdjustmentService();
        try (InputStream targetNetPositionsStream = getClass().getResourceAsStream("/targetNetPositions.json")) {
            Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);
            List<BalanceComputationArea> balanceComputationAreas = balancesAdjustmentService.createBalanceComputationAreas(testNetwork, targetNetPositions);

            assertEquals(4, targetNetPositions.size());
            assertEquals(-1100.3, targetNetPositions.get("BE"), 0.001);
            assertEquals(-3527.5, targetNetPositions.get("DE"), 0.001);
            assertEquals(5925.7, targetNetPositions.get("FR"), 0.001);
            assertEquals(2398.2, targetNetPositions.get("NL"), 0.001);

            // BELGIUM
            assertEquals(4, balanceComputationAreas.size());
            assertEquals("BELGIUM", balanceComputationAreas.get(0).getName());
            assertEquals(-1100.3, balanceComputationAreas.get(0).getTargetNetPosition(), 0.001);
            assertEquals(-7000, balanceComputationAreas.get(0).getScalable().initialValue(testNetwork), 0.001);
            assertEquals(0, balanceComputationAreas.get(0).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            assertEquals(27000, balanceComputationAreas.get(0).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            List<Injection> injections = new ArrayList<>();
            List<String> notFound = new ArrayList<>();
            balanceComputationAreas.get(0).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("BBE1AA1 _generator", injections.get(0).getId());
            assertEquals("BBE3AA1 _generator", injections.get(1).getId());
            assertEquals("BBE2AA1 _generator", injections.get(2).getId());
            assertEquals(1500, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2500, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.001);
            assertEquals(3000, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 500, Scalable.ScalingConvention.GENERATOR);
            assertEquals(1607.14285, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2678.5714, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.001);
            assertEquals(3214.2857, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 20000, Scalable.ScalingConvention.GENERATOR);
            assertEquals(5892.8571, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(testNetwork.getGenerator("BBE3AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.001);
            assertEquals(testNetwork.getGenerator("BBE2AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(0).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.001);

            // FRANCE
            injections.clear();
            notFound.clear();
            assertEquals("FRANCE", balanceComputationAreas.get(1).getName());
            assertEquals(5925.7, balanceComputationAreas.get(1).getTargetNetPosition(), 0.001);
            assertEquals(-7000, balanceComputationAreas.get(1).getScalable().initialValue(testNetwork), 0.001);
            assertEquals(0, balanceComputationAreas.get(1).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            assertEquals(27000, balanceComputationAreas.get(1).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            balanceComputationAreas.get(1).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("FFR1AA1 _generator", injections.get(0).getId());
            assertEquals("FFR2AA1 _generator", injections.get(1).getId());
            assertEquals("FFR3AA1 _generator", injections.get(2).getId());
            assertEquals(2000, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2000, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.001);
            assertEquals(3000, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(1).getScalable().scale(testNetwork, 1000, Scalable.ScalingConvention.GENERATOR);
            assertEquals(2285.7142, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2285.7142, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.001);
            assertEquals(3428.5714, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(1).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.001);

            // GERMANY
            injections.clear();
            notFound.clear();
            assertEquals("GERMANY", balanceComputationAreas.get(2).getName());
            assertEquals(-3527.5, balanceComputationAreas.get(2).getTargetNetPosition(), 0.001);
            assertEquals(-6000, balanceComputationAreas.get(2).getScalable().initialValue(testNetwork), 0.001);
            assertEquals(0, balanceComputationAreas.get(2).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            assertEquals(27000, balanceComputationAreas.get(2).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            balanceComputationAreas.get(2).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("DDE1AA1 _generator", injections.get(0).getId());
            assertEquals("DDE2AA1 _generator", injections.get(1).getId());
            assertEquals("DDE3AA1 _generator", injections.get(2).getId());
            assertEquals(2500, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2000, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.001);
            assertEquals(1500, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(2).getScalable().scale(testNetwork, 800, Scalable.ScalingConvention.GENERATOR);
            assertEquals(2833.3333, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(2266.6666, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.001);
            assertEquals(1700, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(2).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.001);

            // NETHERLANDS
            injections.clear();
            notFound.clear();
            assertEquals("NETHERLANDS", balanceComputationAreas.get(3).getName());
            assertEquals(2398.2, balanceComputationAreas.get(3).getTargetNetPosition(), 0.001);
            assertEquals(-4500, balanceComputationAreas.get(3).getScalable().initialValue(testNetwork), 0.001);
            assertEquals(0, balanceComputationAreas.get(3).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            assertEquals(27000, balanceComputationAreas.get(3).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.001);
            balanceComputationAreas.get(3).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("NNL1AA1 _generator", injections.get(0).getId());
            assertEquals("NNL2AA1 _generator", injections.get(1).getId());
            assertEquals("NNL3AA1 _generator", injections.get(2).getId());
            assertEquals(1500, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.001);
            assertEquals(500, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.001);
            assertEquals(2500, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(3).getScalable().scale(testNetwork, 1500, Scalable.ScalingConvention.GENERATOR);
            assertEquals(2000, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.001);
            assertEquals(666.6666, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.001);
            assertEquals(3333.3333, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.001);
            balanceComputationAreas.get(3).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.001);
            assertEquals(0, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.001);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
