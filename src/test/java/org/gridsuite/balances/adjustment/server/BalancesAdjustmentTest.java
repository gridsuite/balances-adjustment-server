package org.gridsuite.balances.adjustment.server;

import com.powsybl.balances_adjustment.balance_computation.BalanceComputationArea;
import com.powsybl.iidm.import_.Importers;
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
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
        try (InputStream targetNetPositionsStream = new FileInputStream(ResourceUtils.getFile("classpath:targetNetPositions.json"))) {
            Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);
            List<BalanceComputationArea> balanceComputationAreas = balancesAdjustmentService.createBalanceComputationAreas(testNetwork, targetNetPositions);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
