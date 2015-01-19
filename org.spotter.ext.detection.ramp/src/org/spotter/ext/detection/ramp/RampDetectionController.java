/**
 * Copyright 2014 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spotter.ext.detection.ramp;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.Parameter;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;
import org.spotter.shared.status.DiagnosisStatus;

/**
 * The Ramp antipattern detection controller.
 * 
 * @author Alexander Wert
 * 
 */
public class RampDetectionController extends AbstractDetectionController {

	private static final int SAMPLING_DELAY = 100;

	private static final Logger LOGGER = LoggerFactory.getLogger(RampDetectionController.class);

	private static final String STEP = "step";

	private static int stimulationPhaseDuration;
	private static int experimentSteps;
	private static int reuiqredSignificanceSteps;
	private static double requiredSignificanceLevel;
	private static double maxCpuUtilization;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider.
	 */
	public RampDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException {
		try {

			instrumentApplication(getInstrumentationDescription());

			for (int i = 1; i <= experimentSteps; i++) {

				LOGGER.info("RampDetectionController step count ----{}----.", i);

				LOGGER.info("RampDetectionController started to stimulate the SUT with {} users.", GlobalConfiguration
						.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS));
				stimulateSystem(stimulationPhaseDuration);
				LOGGER.info("RampDetectionController finalized to stimulate the SUT.");

				LOGGER.info("RampDetectionController started to run a single user experiment.");
				runExperiment(this.getClass(), 1, i);
				LOGGER.info("RampDetectionController finalized to run a single user experiment.");

			}

			uninstrumentApplication();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	protected void runExperiment(Class<? extends IDetectionController> detectionControllerClass, int numUsers,
			int stepNumber) throws WorkloadException, MeasurementException {

		LOGGER.info("{} started experiment with {} users ...", detectionControllerClass.getSimpleName(), numUsers);

		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(numUsers);
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_DURATION));

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_RAMP_UP);
		getWorkloadAdapter().startLoad(lConfig);

		getWorkloadAdapter().waitForWarmupPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_STABLE_PHASE);
		getMeasurementController().enableMonitoring();

		getWorkloadAdapter().waitForExperimentPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_COOL_DOWN);
		getMeasurementController().disableMonitoring();

		getWorkloadAdapter().waitForFinishedLoad();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.COLLECTING_DATA);
		LOGGER.info("Storing data ...");
		long dataCollectionStart = System.currentTimeMillis();
		Parameter numOfUsersParameter = new Parameter(STEP, stepNumber);
		Set<Parameter> parameters = new TreeSet<>();
		parameters.add(numOfUsersParameter);
		getResultManager().storeResults(parameters, getMeasurementController());
		ProgressManager.getInstance()
				.addAdditionalDuration((System.currentTimeMillis() - dataCollectionStart) / SECOND);
		LOGGER.info("Data stored!");
	}

	private void stimulateSystem(int duration) throws WorkloadException {
		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS));
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(duration);
		getWorkloadAdapter().startLoad(lConfig);

		getWorkloadAdapter().waitForFinishedLoad();
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		if (cpuUtilized(data.getDataSets(CPUUtilizationRecord.class))) {
			result.addMessage("CPU Utilization is quite high. The CPU is probably a bottleneck!");
			result.setDetected(false);
			return result;
		}

		Dataset rtDataset = data.getDataSets(ResponseTimeRecord.class).get(0);

		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {

			boolean operationDetected = analyseOperationResponseTimes(rtDataset, operation);
			if (operationDetected) {
				result.setDetected(true);
				result.addMessage("Ramp detected in operation: " + operation);
			}

		}

		return result;
	}

	private InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY);
		return idBuilder.build();

	}

	private boolean cpuUtilized(List<Dataset> dataSets) {
		Dataset wDataset = dataSets.get(0);
		boolean cpuUtilized = false;

		ParameterSelection parSelection = new ParameterSelection();
		parSelection.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED);

		for (Integer numUsers : wDataset.getValueSet(STEP, Integer.class)) {
			parSelection.select(STEP, numUsers);
			Dataset selectedDataSet = parSelection.applyTo(wDataset);

			double meanCpuUtil = LpeNumericUtils.average(selectedDataSet.getValues(
					CPUUtilizationRecord.PAR_UTILIZATION, Double.class));
			if (meanCpuUtil >= maxCpuUtilization) {
				cpuUtilized = true;
			}
		}

		return cpuUtilized;
	}

	private boolean analyseOperationResponseTimes(Dataset rtDataset, String operation) {
		int prevStep = -1;
		int firstSignificantStep = -1;
		int significantSteps = 0;
		for (Integer step : rtDataset.getValueSet(STEP, Integer.class)) {
			if (prevStep > 0) {
				ParameterSelection selectionCurrent = new ParameterSelection().select(STEP, step).select(
						ResponseTimeRecord.PAR_OPERATION, operation);
				ParameterSelection selectionPrev = new ParameterSelection().select(STEP, prevStep).select(
						ResponseTimeRecord.PAR_OPERATION, operation);

				Dataset datasetCurrent = selectionCurrent.applyTo(rtDataset);
				Dataset datasetPrev = selectionPrev.applyTo(rtDataset);

				// maybe the operation could not be found in one of the current
				// selections
				if (datasetCurrent == null || datasetPrev == null) {
					prevStep = step;
					continue;
				}

				double pValue = LpeNumericUtils.tTest(
						datasetCurrent.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class),
						datasetPrev.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));

				if (pValue <= requiredSignificanceLevel) {
					if (firstSignificantStep < 0) {
						firstSignificantStep = prevStep;
					}
					significantSteps++;
				} else if (pValue > requiredSignificanceLevel) {
					firstSignificantStep = -1;
					significantSteps = 0;
				}
			}
			prevStep = step;
		}
		if (firstSignificantStep > 0 && significantSteps >= reuiqredSignificanceSteps) {
			return true;
		}
		return false;
	}

	@Override
	public void loadProperties() {
		String warmupPhaseStr = getProblemDetectionConfiguration().getProperty(RampExtension.KEY_WARMUP_PHASE_DURATION);
		stimulationPhaseDuration = warmupPhaseStr != null ? Integer.parseInt(warmupPhaseStr)
				: RampExtension.STIMULATION_PHASE_DURATION_DEFAULT;

		String experimentStepsStr = getProblemDetectionConfiguration().getProperty(RampExtension.KEY_EXPERIMENT_STEPS);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: RampExtension.EXPERIMENT_STEPS_DEFAULT;

		String significanceStepsStr = getProblemDetectionConfiguration().getProperty(
				RampExtension.KEY_REQUIRED_SIGNIFICANT_STEPS);
		reuiqredSignificanceSteps = significanceStepsStr != null ? Integer.parseInt(significanceStepsStr)
				: RampExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String significanceLevelStr = getProblemDetectionConfiguration().getProperty(
				RampExtension.KEY_REQUIRED_SIGNIFICANCE_LEVEL);
		requiredSignificanceLevel = significanceLevelStr != null ? Integer.parseInt(significanceLevelStr)
				: RampExtension.REQUIRED_SIGNIFICANCE_LEVEL_DEFAULT;

		String maxCpuUtilizationStr = getProblemDetectionConfiguration().getProperty(
				RampExtension.KEY_CPU_UTILIZATION_THRESHOLD);
		maxCpuUtilization = maxCpuUtilizationStr != null ? Integer.parseInt(maxCpuUtilizationStr)
				: RampExtension.MAX_CPU_UTILIZATION_DEFAULT;
	}

	@Override
	public long getExperimentSeriesDuration() {
		long experimentDuration = ProgressManager.getInstance().calculateExperimentDuration(1,
				GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.EXPERIMENT_DURATION));
		long stimulationDuration = ProgressManager.getInstance().calculateExperimentDuration(
				GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS),
				stimulationPhaseDuration);

		return ((long) experimentSteps) * (stimulationDuration + experimentDuration);
	}

}
