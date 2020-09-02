/*
 * Copyright © 2019-2020 Cask Data, Inc.
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

import { constructUrl } from 'server/url-helper';
import { getCDAPConfig } from 'server/cdap-config';
import { getGETRequestOptions, requestPromiseWrapper } from 'gql/resolvers-common';

let cdapConfig;
getCDAPConfig().then(function(value) {
  cdapConfig = value;
});

export async function queryTypeStatusResolver(parent, args, context) {
  const options = getGETRequestOptions();
  options.url = constructUrl(cdapConfig, '/ping');

  const status = await requestPromiseWrapper(options, context.auth);

  return status.trim();
}
