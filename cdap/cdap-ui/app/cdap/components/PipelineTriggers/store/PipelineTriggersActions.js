/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

const PipelineTriggersActions = {
  changeNamespace: 'TRIGGERS_CHANGE_NAMESPACE',
  setPipeline: 'TRIGGERS_SET_PIPELINE',
  setExpandedPipeline: 'TRIGGERS_SET_EXPANDED_PIPELINE',
  setExpandedTrigger: 'TRIGGERS_SET_EXPANDED_TRIGGER',
  setTriggersAndPipelineList: 'TRIGGERS_SET_TRIGGERS_PIPELINE',
  setEnabledTriggerPipelineInfo: 'TRIGGERS_SET_PIPELINE_INFO',
  reset: 'TRIGGERS_RESET',
};

export default PipelineTriggersActions;
