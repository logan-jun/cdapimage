/*
 * Copyright © 2017-2018 Cask Data, Inc.
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

package io.cdap.mmds.modeler.param;

import io.cdap.mmds.spec.DoubleParam;
import io.cdap.mmds.spec.IntParam;
import io.cdap.mmds.spec.ParamSpec;
import io.cdap.mmds.spec.Parameters;
import io.cdap.mmds.spec.Params;
import io.cdap.mmds.spec.Range;
import org.apache.spark.ml.tree.DecisionTreeParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common modeler parameters for tree based algorithms.
 */
public class TreeParams implements Parameters {
  private IntParam maxDepth;
  private IntParam maxBins;
  private IntParam minInstancesPerNode;
  private DoubleParam minInfoGain;

  public TreeParams(Map<String, String> modelParams) {
    this.maxDepth = new IntParam("maxDepth", "Max Depth",
                                 "Maximum depth of the tree. " +
                                   "For example, depth 0 means 1 leaf node, " +
                                   "depth 1 means 1 internal node + 2 leaf nodes.",
                                 5, new Range(0, true), modelParams);
    this.maxBins = new IntParam("maxBins", "Max Bins",
                                "Maximum number of bins used for discretizing continuous features and for " +
                                  "choosing how to split on features at each node. " +
                                  "More bins give higher granularity. Must be greater than or equal to the " +
                                  "number of categories in any categorical feature.",
                                32, new Range(2, true), modelParams);
    this.minInstancesPerNode = new IntParam("minInstancesPerNode", "Min Instances Per Node",
                                            "Minimum number of instances each child must have after split. " +
                                              "If a split causes the left or right child to have fewer than " +
                                              "minInstancesPerNode, the split will be discarded as invalid.",
                                            1, new Range(1, true), modelParams);
    this.minInfoGain = new DoubleParam("minInfoGain", "Min Info Gain",
                                       "Minimum information gain for a split to be considered at a tree node.",
                                       0d, new Range(0d, true), modelParams);
  }

  @Override
  public Map<String, String> toMap() {
    return Params.putParams(new HashMap<>(), maxDepth, maxBins, minInstancesPerNode, minInfoGain);
  }

  @Override
  public List<ParamSpec> getSpec() {
    return Params.addParams(new ArrayList<>(), maxDepth, maxBins, minInstancesPerNode, minInfoGain);
  }

  public void setParams(DecisionTreeParams params) {
    params.setMaxDepth(maxDepth.getVal());
    params.setMaxBins(maxBins.getVal());
    params.setMinInstancesPerNode(minInstancesPerNode.getVal());
    params.setMinInfoGain(minInfoGain.getVal());
  }
}
