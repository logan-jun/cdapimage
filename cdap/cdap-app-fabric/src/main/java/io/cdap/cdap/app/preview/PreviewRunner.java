/*
 * Copyright © 2016-2020 Cask Data, Inc.
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

package io.cdap.cdap.app.preview;

import io.cdap.cdap.proto.id.ProgramId;

import java.util.concurrent.Future;

/**
 * Interface responsible for managing the lifecycle of a single preview application
 * and retrieving the data generated by that application.
 */
public interface PreviewRunner {

  /**
   * Start the preview of an application. The returned {@link Future} will be completed when the preview completed.
   *
   * @param previewRequest request representing preview
   * @throws Exception when there is error while starting preview
   */
  Future<PreviewRequest> startPreview(PreviewRequest previewRequest) throws Exception;

  /**
   * Stop the preview run represented by this {@link ProgramId}.
   * @param programId id of the preview program to be stopped
   * @throws Exception thrown when any error in stopping the preview run
   */
  void stopPreview(ProgramId programId) throws Exception;
}