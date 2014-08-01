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
package org.spotter.ext.detection.olb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

import com.xeiam.xchart.Chart;

/**
 * Detection controller for the One Lane Bridge.
 * 
 * @author Alexander Wert
 * 
 */
public class OLBDetectionController extends AbstractDetectionController {

	private static final int SAMPLING_DELAY = 200;

	private static final double PER_PERCENT = 0.01;

	private int experimentSteps;
	private int requiredSignificantSteps;
	private double cpuThreshold;
	private double requiredSignificanceLevel;
	private String scope;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider.
	 */
	public OLBDetectionController(IExtension<IDetectionController> provider) {
		super(provider);

	}

	@Override
	protected void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(OLBDetectionController.class, experimentSteps, getInstrumentationDescription());
	}

	@Override
	public void loadProperties() {

		String experimentStepsStr = getProblemDetectionConfiguration().getProperty(OLBExtension.EXPERIMENT_STEPS_KEY);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: OLBExtension.EXPERIMENT_STEPS_DEFAULT;

		String requiredSignificantStepsStr = getProblemDetectionConfiguration().getProperty(
				OLBExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: OLBExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String cpuThreasholdStr = getProblemDetectionConfiguration().getProperty(
				OLBExtension.CPU_UTILIZATION_THRESHOLD_KEY);
		cpuThreshold = (cpuThreasholdStr != null ? Double.parseDouble(cpuThreasholdStr)
				: OLBExtension.CPU_UTILIZATION_THRESHOLD_DEFAULT) * PER_PERCENT;

		String requiredConfidenceLevelStr = getProblemDetectionConfiguration().getProperty(
				OLBExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr) : OLBExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

		scope = getProblemDetectionConfiguration().getProperty(OLBExtension.OLB_SCOPE_KEY);
		scope = scope == null ? OLBExtension.OLB_SCOPE_ENTRY_POINT : scope;
	}

	private InstrumentationDescription getInstrumentationDescription() {

		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();

		switch (scope) {
		case OLBExtension.OLB_SCOPE_ENTRY_POINT:
			idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE).entityDone();
			break;
		case OLBExtension.OLB_SCOPE_SYNC:
			// TODO: add sync instrumentation
			break;
		default:
			throw new IllegalArgumentException("Invalid Scope value for OLB detection!");
		}

		return idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY).build();

	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {

		SpotterResult result = new SpotterResult();

		Map<Integer, Double> cpuMeans = new HashMap<>();

		Dataset cpuUtilDataset = data.getDataSet(CPUUtilizationRecord.class);
		if (cpuUtilDataset == null) {
			result.addMessage("Unable to analyze CPU utilization: CPU utilization data has not been gathered.");
		} else {
			boolean cpuUtilized = cpuUtilized(cpuUtilDataset, cpuMeans);

			Chart utilChart = OLBImageExporter.createCpuUtilChart(cpuMeans, cpuThreshold);
			getResultManager().storeImageChartResource(utilChart, "CPUUtilization", result);

			if (cpuUtilized) {
				result.addMessage("CPU Utilization is quite high. The CPU is probably a bottleneck!");
				result.setDetected(false);
				return result;
			}
		}

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}

		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			Map<Integer, Double> rtMeans = new HashMap<>();
			Map<Integer, Double> rtStDevs = new HashMap<>();

			boolean operationDetected = false;
			try {
				operationDetected = analyseOperationResponseTimes(rtDataset, operation, rtMeans, rtStDevs);
			} catch (NullPointerException npe) {
				result.addMessage("OLB detection failed for the operation '" + operation
						+ "', because the operation was not executed in each analysis cycle. "
						+ "Hence, the operation cannot be analyzed for an OLB.");
				continue;
			}

			if (operationDetected) {
				result.setDetected(true);
				
				switch (scope) {
				case OLBExtension.OLB_SCOPE_ENTRY_POINT:
					result.addMessage("OLB detected in service: " + operation);
					break;
				case OLBExtension.OLB_SCOPE_SYNC:
					result.addMessage("OLB detected in synchronized operation: " + operation);
					break;
				default:
					throw new IllegalArgumentException("Invalid Scope value for OLB detection!");
				}
				

				Chart rtChart = OLBImageExporter.createOperationRTChart(operation, rtMeans, rtStDevs);
				getResultManager().storeImageChartResource(rtChart, "RT-" + operation.replace("\\.", "_"), result);
			}
		}

		return result;
	}

	private boolean cpuUtilized(Dataset wDataset, final Map<Integer, Double> cpuMeans) {

		boolean cpuUtilized = false;

		ParameterSelection parSelection = new ParameterSelection();
		parSelection.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED);

		for (Integer numUsers : wDataset.getValueSet(NUMBER_OF_USERS_KEY, Integer.class)) {
			parSelection.select(NUMBER_OF_USERS_KEY, numUsers);
			Dataset selectedDataSet = parSelection.applyTo(wDataset);

			double meanCpuUtil = LpeNumericUtils.average(selectedDataSet.getValues(
					CPUUtilizationRecord.PAR_UTILIZATION, Double.class));
			if (meanCpuUtil >= cpuThreshold) {
				cpuUtilized = true;
			}
			cpuMeans.put(numUsers, meanCpuUtil);
		}

		return cpuUtilized;
	}

	private boolean analyseOperationResponseTimes(Dataset dataset, String operation, Map<Integer, Double> rtMeans,
			Map<Integer, Double> rtStDevs) {
		int prevNumUsers = -1;
		int firstSignificantNumUsers = -1;
		int significantSteps = 0;
		List<Integer> sortedNumUsersList = new ArrayList<Integer>(dataset.getValueSet(NUMBER_OF_USERS_KEY,
				Integer.class));
		Collections.sort(sortedNumUsersList);
		double currentMean = -1;
		double prevMean = -1;
		double currentStDev = -1;
		double prevStDev = -1;
		for (Integer numUsers : sortedNumUsersList) {
			if (prevNumUsers > 0) {
				ParameterSelection selectionCurrent = new ParameterSelection().select(NUMBER_OF_USERS_KEY, numUsers)
						.select(ResponseTimeRecord.PAR_OPERATION, operation);
				ParameterSelection selectionPrev = new ParameterSelection().select(NUMBER_OF_USERS_KEY, prevNumUsers)
						.select(ResponseTimeRecord.PAR_OPERATION, operation);

				List<Long> currentValues = selectionCurrent.applyTo(dataset).getValues(
						ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class);
				List<Long> prevValues = selectionPrev.applyTo(dataset).getValues(ResponseTimeRecord.PAR_RESPONSE_TIME,
						Long.class);
				if (currentValues.size() < 2 || prevValues.size() < 2) {
					return false;
				}
				if (currentMean < 0) {
					prevMean = LpeNumericUtils.average(prevValues);
					prevStDev = LpeNumericUtils.stdDev(prevValues);
					rtMeans.put(prevNumUsers, prevMean);
					rtStDevs.put(prevNumUsers, prevStDev);
				} else {
					prevMean = currentMean;
					prevStDev = currentStDev;
				}
				currentMean = LpeNumericUtils.average(currentValues);
				currentStDev = LpeNumericUtils.stdDev(currentValues);
				rtMeans.put(numUsers, currentMean);
				rtStDevs.put(numUsers, currentStDev);

				double pValue = LpeNumericUtils.tTest(currentValues, prevValues);
				if (pValue >= 0 && pValue <= requiredSignificanceLevel && prevMean < currentMean) {
					if (firstSignificantNumUsers < 0) {
						firstSignificantNumUsers = prevNumUsers;
					}
					significantSteps++;
				} else {
					firstSignificantNumUsers = -1;
					significantSteps = 0;
				}
			}
			prevNumUsers = numUsers;

		}

		if (firstSignificantNumUsers > 0 && significantSteps >= requiredSignificantSteps) {
			return true;
		}
		return false;

	}

	@Override
	public int getNumOfExperiments() {
		return experimentSteps;
	}

}
