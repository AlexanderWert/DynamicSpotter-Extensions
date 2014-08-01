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
package org.spotter.ext.dummy;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.description.InstrumentationDescription;
import org.lpe.common.extension.IExtension;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

public class TestDetection extends AbstractDetectionController {

	public TestDetection(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {

	}

	@Override
	protected void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(TestDetection.class, 1, new InstrumentationDescription());
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {

		SpotterResult result = new SpotterResult();
		result.setDetected(true);
		result.addMessage("Detection run finished successfully!");

		return result;
	}

	@Override
	public int getNumOfExperiments() {
		return 1;
	}

}
