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
package org.spotter.ext.workload.simple;

/**
 * Interface representing a vUser script. Has to be implemented by all scripts for the simple workload driver.
 * @author Alexander Wert
 *
 */
public interface ISimpleVUser {
	/**
	 * Executes on vUser iteration.
	 */
	void executeIteration();
}
