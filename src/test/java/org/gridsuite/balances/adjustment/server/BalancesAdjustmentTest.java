package org.gridsuite.balances.adjustment.server;

import com.powsybl.balances_adjustment.balance_computation.BalanceComputationArea;
import com.powsybl.balances_adjustment.balance_computation.BalanceComputationParameters;
import com.powsybl.balances_adjustment.balance_computation.BalanceComputationResult;
import com.powsybl.balances_adjustment.balance_computation.json_parameters.JsonBalanceComputationParameters;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.modification.scalable.ScalingParameters;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.balances.adjustment.server.importer.TargetNetPositionsImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalancesAdjustmentController.class)
@ContextConfiguration(classes = {BalancesAdjustmentApplication.class})
class BalancesAdjustmentTest {

    private Network testNetwork;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @BeforeEach
    void setUp() {
        testNetwork = Network.read("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testBalancesAdjustmentController() throws Exception {
        UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.COLLECTION)).willReturn(testNetwork);

        MockMultipartFile file = new MockMultipartFile("targetNetPositionFile", "workingTargetNetPositions.json",
                "text/json", new FileInputStream(ResourceUtils.getFile("classpath:workingTargetNetPositions.json")));

        MockMultipartFile parametersFile = new MockMultipartFile("balanceComputationParamsFile", "balanceComputationParameters.json",
                "text/json", new FileInputStream(ResourceUtils.getFile("classpath:balanceComputationParameters.json")));

        MockMultipartHttpServletRequestBuilder builderOk =
                MockMvcRequestBuilders.multipart("/v1/networks/{networkUuid}/run", testNetworkId.toString());
        builderOk.with(request -> {
            request.setMethod("PUT");
            return request;
        });

        // Check request is ok with only target net positions multipart file provided
        MvcResult result = mvc.perform(builderOk
                .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"SUCCESS\""));
        assertTrue(result.getResponse().getContentAsString().contains("iterationCount\":2"));

        // Check request is ok with target net positions multipart file and balance computation parameters file provided
        result = mvc.perform(builderOk
                .file(parametersFile))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"SUCCESS\""));

        // Check request is ko when no target net position multipart file is provided
        MockMultipartHttpServletRequestBuilder builderKo =
                MockMvcRequestBuilders.multipart("/v1/networks/{networkUuid}/run", testNetworkId.toString());
        builderKo.with(request -> {
            request.setMethod("PUT");
            return request;
        });

        mvc.perform(builderKo)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBalancesAdjustmentControllerWithMergingView() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        Network testNetwork2 = Network.read("testCase2.xiidm", getClass().getResourceAsStream("/testCase2.xiidm"));
        Network testNetwork3 = Network.read("testCase3.xiidm", getClass().getResourceAsStream("/testCase3.xiidm"));
        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.COLLECTION)).willReturn(testNetwork);
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.COLLECTION)).willReturn(testNetwork2);
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.COLLECTION)).willReturn(testNetwork3);

        MockMultipartFile file = new MockMultipartFile("targetNetPositionFile", "workingTargetNetPositions.json",
                "text/json", new FileInputStream(ResourceUtils.getFile("classpath:workingTargetNetPositions.json")));

        MockMultipartFile parametersFile = new MockMultipartFile("balanceComputationParamsFile", "balanceComputationParameters2.json",
                "text/json", new FileInputStream(ResourceUtils.getFile("classpath:balanceComputationParameters2.json")));

        MockMultipartHttpServletRequestBuilder builderOk =
                MockMvcRequestBuilders.multipart("/v1/networks/{networkUuid}/run", testNetworkId1.toString());
        builderOk.param("networkUuid", testNetworkId2.toString()).param("networkUuid", testNetworkId3.toString());
        builderOk.with(request -> {
            request.setMethod("PUT");
            return request;
        });

        // Check request is ok with target net positions and balances computation parameters multipart file provided
        MvcResult result = mvc.perform(builderOk
                .file(file)
                .file(parametersFile))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"SUCCESS\""));
        assertTrue(result.getResponse().getContentAsString().contains("iterationCount\":2"));

        // Check request is ko when no target net position multipart file is provided
        MockMultipartHttpServletRequestBuilder builderKo =
                MockMvcRequestBuilders.multipart("/v1/networks/{networkUuid}/run", testNetworkId1.toString());
        builderKo.param("networkUuid", testNetworkId2.toString()).param("networkUuid", testNetworkId3.toString());
        builderKo.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        mvc.perform(builderKo)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSuccessBalancesAdjustmentComputation() throws Exception {
        UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.COLLECTION)).willReturn(testNetwork);

        InputStream balanceComputationParametersIStream = new FileInputStream(ResourceUtils.getFile("classpath:balanceComputationParameters.json"));
        BalanceComputationParameters balanceComputationParameters = JsonBalanceComputationParameters.read(balanceComputationParametersIStream);

        InputStream targetNetPositionsIStream = new FileInputStream(ResourceUtils.getFile("classpath:workingTargetNetPositions.json"));
        BalanceComputationResult balanceComputationResult = balancesAdjustmentService.computeBalancesAdjustment(testNetworkId, balanceComputationParameters, targetNetPositionsIStream);
        assertEquals(BalanceComputationResult.Status.SUCCESS, balanceComputationResult.getStatus());
        assertEquals(2, balanceComputationResult.getIterationCount());

        // BELGIUM
        assertEquals(724.8642, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
        assertEquals(1208.1071, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
        assertEquals(1449.6120, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);

        // FRANCE
        assertEquals(3143.6285, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
        assertEquals(3143.6285, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
        assertEquals(4715.5596, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);

        // GERMANY
        assertEquals(1665.2083, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.1);
        assertEquals(1332.1666, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.1);
        assertEquals(999.1249, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.1);

        // NETHERLANDS
        assertEquals(2039.3999, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
        assertEquals(679.7999, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
        assertEquals(3399.0000, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
    }

    @Test
    void testFailedWorkingBalancesAdjustmentComputation() throws Exception {
        UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.COLLECTION)).willReturn(testNetwork);

        InputStream balanceComputationParametersIStream = new FileInputStream(ResourceUtils.getFile("classpath:balanceComputationParameters.json"));
        BalanceComputationParameters balanceComputationParameters = JsonBalanceComputationParameters.read(balanceComputationParametersIStream);

        InputStream targetNetPositionsIStream = new FileInputStream(ResourceUtils.getFile("classpath:failingTargetNetPositions.json"));
        BalanceComputationResult balanceComputationResult = balancesAdjustmentService.computeBalancesAdjustment(testNetworkId, balanceComputationParameters, targetNetPositionsIStream, false);
        assertEquals(BalanceComputationResult.Status.FAILED, balanceComputationResult.getStatus());
        assertEquals(11, balanceComputationResult.getIterationCount());
    }

    @Test
    void testNetworkComputationAreasCreationNoIterativeMode() throws Exception {
        try (InputStream targetNetPositionsStream = new FileInputStream(ResourceUtils.getFile("classpath:workingTargetNetPositions.json"))) {
            Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);

            // target net positions read from file
            assertEquals(4, targetNetPositions.size());
            assertEquals(-2117.3, targetNetPositions.get("BE"), 0.1);
            assertEquals(-4503.5, targetNetPositions.get("DE"), 0.1);
            assertEquals(5002.7, targetNetPositions.get("FR"), 0.1);
            assertEquals(1618.2, targetNetPositions.get("NL"), 0.1);

            List<BalanceComputationArea> balanceComputationAreas = balancesAdjustmentService.createBalanceComputationAreas(testNetwork, targetNetPositions, true);

            // target net positions not changed in the balance computation areas creation
            assertEquals(4, targetNetPositions.size());
            assertEquals(-2117.3, targetNetPositions.get("BE"), 0.1);
            assertEquals(-4503.5, targetNetPositions.get("DE"), 0.1);
            assertEquals(5002.7, targetNetPositions.get("FR"), 0.1);
            assertEquals(1618.2, targetNetPositions.get("NL"), 0.1);

            // BELGIUM
            assertEquals(4, balanceComputationAreas.size());
            assertEquals("BELGIUM", balanceComputationAreas.get(0).getName());
            assertEquals(-7000, balanceComputationAreas.get(0).getScalable().initialValue(testNetwork), 0.1);
            assertEquals(0, balanceComputationAreas.get(0).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            assertEquals(27000, balanceComputationAreas.get(0).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            List<Injection> injections = new ArrayList<>();
            List<String> notFound = new ArrayList<>();
            balanceComputationAreas.get(0).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("BBE1AA1 _generator", injections.get(0).getId());
            assertEquals("BBE3AA1 _generator", injections.get(1).getId());
            assertEquals("BBE2AA1 _generator", injections.get(2).getId());
            assertEquals(1500, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2500, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(3000, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 500, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(1607.14285, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2678.5714, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(3214.2857, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 20000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(5892.8571, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("BBE3AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("BBE2AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(0).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);

            // FRANCE
            injections.clear();
            notFound.clear();
            assertEquals("FRANCE", balanceComputationAreas.get(1).getName());
            assertEquals(-7000, balanceComputationAreas.get(1).getScalable().initialValue(testNetwork), 0.1);
            assertEquals(0, balanceComputationAreas.get(1).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            assertEquals(27000, balanceComputationAreas.get(1).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            balanceComputationAreas.get(1).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("FFR1AA1 _generator", injections.get(0).getId());
            assertEquals("FFR2AA1 _generator", injections.get(1).getId());
            assertEquals("FFR3AA1 _generator", injections.get(2).getId());
            assertEquals(2000, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2000, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
            assertEquals(3000, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(1).getScalable().scale(testNetwork, 1000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(2285.7142, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2285.7142, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
            assertEquals(3428.5714, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(1).getScalable().scale(testNetwork, 15000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(6571.4285, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
            assertEquals(6571.4285, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("FFR3AA1 _generator").getMaxP(), testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(1).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);

            // GERMANY
            injections.clear();
            notFound.clear();
            assertEquals("GERMANY", balanceComputationAreas.get(2).getName());
            assertEquals(-6000, balanceComputationAreas.get(2).getScalable().initialValue(testNetwork), 0.1);
            assertEquals(0, balanceComputationAreas.get(2).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            assertEquals(27000, balanceComputationAreas.get(2).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            balanceComputationAreas.get(2).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("DDE1AA1 _generator", injections.get(0).getId());
            assertEquals("DDE2AA1 _generator", injections.get(1).getId());
            assertEquals("DDE3AA1 _generator", injections.get(2).getId());
            assertEquals(2500, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2000, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.1);
            assertEquals(1500, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(2).getScalable().scale(testNetwork, 800, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(2833.3333, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2266.6666, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.1);
            assertEquals(1700, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(2).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.1);

            // NETHERLANDS
            injections.clear();
            notFound.clear();
            assertEquals("NETHERLANDS", balanceComputationAreas.get(3).getName());
            assertEquals(-4500, balanceComputationAreas.get(3).getScalable().initialValue(testNetwork), 0.1);
            assertEquals(0, balanceComputationAreas.get(3).getScalable().minimumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            assertEquals(27000, balanceComputationAreas.get(3).getScalable().maximumValue(testNetwork, Scalable.ScalingConvention.GENERATOR), 0.1);
            balanceComputationAreas.get(3).getScalable().filterInjections(testNetwork, injections, notFound);
            assertEquals(3, injections.size());
            assertTrue(notFound.isEmpty());
            assertEquals("NNL1AA1 _generator", injections.get(0).getId());
            assertEquals("NNL2AA1 _generator", injections.get(1).getId());
            assertEquals("NNL3AA1 _generator", injections.get(2).getId());
            assertEquals(1500, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
            assertEquals(500, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
            assertEquals(2500, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(3).getScalable().scale(testNetwork, 1500, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(2000, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
            assertEquals(666.6666, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
            assertEquals(3333.3333, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(3).getScalable().scale(testNetwork, 25000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR));
            assertEquals(testNetwork.getGenerator("NNL1AA1 _generator").getMaxP(), testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
            assertEquals(3444.4443, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("NNL3AA1 _generator").getMaxP(), testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(3).getScalable().reset(testNetwork);
            assertEquals(0, testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
            assertEquals(0, testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
        }
    }

    @Test
    void testNetworkComputationAreasCreationIterativeMode() throws Exception {
        try (InputStream targetNetPositionsStream = new FileInputStream(ResourceUtils.getFile("classpath:workingTargetNetPositions.json"))) {
            Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);
            List<BalanceComputationArea> balanceComputationAreas = balancesAdjustmentService.createBalanceComputationAreas(testNetwork, targetNetPositions, true);

            // BELGIUM
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 500, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR).setIterative(true));
            assertEquals(1607.14285, testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(2678.5714, testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(3214.2857, testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);
            balanceComputationAreas.get(0).getScalable().scale(testNetwork, 20000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR).setIterative(true));
            assertEquals(testNetwork.getGenerator("BBE1AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("BBE3AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE3AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("BBE2AA1 _generator").getMaxP(), testNetwork.getGenerator("BBE2AA1 _generator").getTargetP(), 0.1);

            // FRANCE
            balanceComputationAreas.get(1).getScalable().scale(testNetwork, 15000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR).setIterative(true));
            assertEquals(6500, testNetwork.getGenerator("FFR1AA1 _generator").getTargetP(), 0.1);
            assertEquals(6500, testNetwork.getGenerator("FFR2AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("FFR3AA1 _generator").getMaxP(), testNetwork.getGenerator("FFR3AA1 _generator").getTargetP(), 0.1);

            // GERMANY
            balanceComputationAreas.get(2).getScalable().scale(testNetwork, 17000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR).setIterative(true));
            assertEquals(testNetwork.getGenerator("DDE1AA1 _generator").getMaxP(), testNetwork.getGenerator("DDE1AA1 _generator").getTargetP(), 0.1);
            assertEquals(8000, testNetwork.getGenerator("DDE2AA1 _generator").getTargetP(), 0.1);
            assertEquals(6000, testNetwork.getGenerator("DDE3AA1 _generator").getTargetP(), 0.1);

            // NETHERLANDS
            balanceComputationAreas.get(3).getScalable().scale(testNetwork, 25000, new ScalingParameters().setScalingConvention(Scalable.ScalingConvention.GENERATOR).setIterative(true));
            assertEquals(testNetwork.getGenerator("NNL1AA1 _generator").getMaxP(), testNetwork.getGenerator("NNL1AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("NNL2AA1 _generator").getMaxP(), testNetwork.getGenerator("NNL2AA1 _generator").getTargetP(), 0.1);
            assertEquals(testNetwork.getGenerator("NNL3AA1 _generator").getMaxP(), testNetwork.getGenerator("NNL3AA1 _generator").getTargetP(), 0.1);
        }
    }

    @Test
    void testTargetNetPositions() throws Exception {
        try (InputStream targetNetPositionsStream = new FileInputStream(ResourceUtils.getFile("classpath:failingTargetNetPositions.json"))) {
            Map<String, Double> targetNetPositions = TargetNetPositionsImporter.getTargetNetPositionsAreasFromFile(targetNetPositionsStream);

            // target net positions read from file
            assertEquals(4, targetNetPositions.size());
            assertEquals(-1100.3, targetNetPositions.get("BE"), 0.1);
            assertEquals(-3527.5, targetNetPositions.get("DE"), 0.1);
            assertEquals(5925.7, targetNetPositions.get("FR"), 0.1);
            assertEquals(2398.2, targetNetPositions.get("NL"), 0.1);

            balancesAdjustmentService.createBalanceComputationAreas(testNetwork, targetNetPositions, true);

            // target net positions adjusted in the balance computation areas creation
            assertEquals(4, targetNetPositions.size());
            assertEquals(-1414.2988, targetNetPositions.get("BE"), 0.1);
            assertEquals(-4534.1626, targetNetPositions.get("DE"), 0.1);
            assertEquals(4234.6494, targetNetPositions.get("FR"), 0.1);
            assertEquals(1713.812, targetNetPositions.get("NL"), 0.1);
        }
    }
}
