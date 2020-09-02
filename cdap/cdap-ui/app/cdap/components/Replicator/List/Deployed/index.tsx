/*
 * Copyright © 2020 Cask Data, Inc.
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

import * as React from 'react';
import withStyles, { WithStyles, StyleRules } from '@material-ui/core/styles/withStyles';
import { MyReplicatorApi } from 'api/replicator';
import { getCurrentNamespace } from 'services/NamespaceStore';
import { PluginType } from 'components/Replicator/constants';
import { objectQuery } from 'services/helpers';
import Status from 'components/Status';
import { Link } from 'react-router-dom';
import ActionsPopover, { IAction } from 'components/ActionsPopover';
import DeleteConfirmation, { InstanceType } from 'components/Replicator/DeleteConfirmation';

const styles = (theme): StyleRules => {
  return {
    root: {
      height: '100%',
    },
    headerText: {
      marginBottom: '10px',
    },
    row: {
      color: theme.palette.grey[50],
      '&:hover': {
        color: 'inherit',
      },
    },
    gridWrapper: {
      // 100% - headerText
      height: 'calc(100% - 20px)',
      '& .grid.grid-container.grid-compact': {
        maxHeight: '100%',
        '& .grid-row': {
          gridTemplateColumns: '2fr 1fr 1fr 80px',
        },
      },
    },
    delete: {
      color: theme.palette.red[100],
    },
  };
};

const DeployedView: React.FC<WithStyles<typeof styles>> = ({ classes }) => {
  const [replicators, setReplicators] = React.useState([]);
  const [statusMap, setStatusMap] = React.useState({});
  const [configMap, setConfigMap] = React.useState({});
  const [replicatorNameDelete, setReplicatorNameDelete] = React.useState(null);

  // TODO: Replace with GraphQL
  function fetchList() {
    const params = {
      namespace: getCurrentNamespace(),
    };

    MyReplicatorApi.list(params).subscribe((list) => {
      setReplicators(list);

      // Batch status
      const batchStatusBody = list.map((replicator) => {
        return {
          appId: replicator.name,
          programType: 'worker',
          programId: 'DeltaWorker',
        };
      });

      MyReplicatorApi.batchStatus(params, batchStatusBody).subscribe((status) => {
        const map = {};

        status.forEach((replicator) => {
          map[replicator.appId] = replicator.status;
        });

        setStatusMap(map);
      });

      const batchDetailBody = list.map((replicator) => {
        return {
          appId: replicator.name,
        };
      });

      MyReplicatorApi.batchAppDetail(params, batchDetailBody).subscribe((apps) => {
        const map = {};

        apps.forEach((app) => {
          if (!app.detail) {
            return;
          }
          const replicator = app.detail;

          let config;
          try {
            config = JSON.parse(replicator.configuration);
          } catch (e) {
            // tslint:disable-next-line: no-console
            console.log('Failed to parse replication pipeline configuration', e);
            return;
          }

          const replicatorObj = {};
          config.stages.forEach((stage) => {
            replicatorObj[stage.plugin.type] = stage.plugin.name;
          });

          map[replicator.name] = replicatorObj;
        });

        setConfigMap(map);
      });
    });
  }

  React.useEffect(fetchList, []);

  return (
    <div className={classes.root}>
      <div className={classes.headerText}>
        {replicators.length} replication {replicators.length === 1 ? 'pipeline' : 'pipelines'} -
        Select a row to view details
      </div>

      <div className={`grid-wrapper ${classes.gridWrapper}`}>
        <div className="grid grid-container grid-compact">
          <div className="grid-header">
            <div className="grid-row">
              <div>Replication pipeline name</div>
              <div>From / To</div>
              <div>Status</div>
              <div />
            </div>
          </div>

          <div className="grid-body">
            {replicators.map((replicator) => {
              const source = objectQuery(configMap, replicator.name, PluginType.source) || '--';
              const target = objectQuery(configMap, replicator.name, PluginType.target) || '--';

              const actions: IAction[] = [
                {
                  label: 'Delete',
                  actionFn: () => setReplicatorNameDelete(replicator.name),
                  className: classes.delete,
                },
              ];

              return (
                <Link
                  to={`/ns/${getCurrentNamespace()}/replication/detail/${replicator.name}`}
                  className={`grid-row ${classes.row}`}
                  key={replicator.name}
                >
                  <div>{replicator.name}</div>
                  <div>
                    {source} / {target}
                  </div>
                  <div>
                    <Status status={statusMap[replicator.name]} />
                  </div>
                  <div>
                    <ActionsPopover actions={actions} />
                  </div>
                </Link>
              );
            })}
          </div>
        </div>
      </div>

      <DeleteConfirmation
        replicatorId={replicatorNameDelete}
        show={replicatorNameDelete && replicatorNameDelete.length > 0}
        onDelete={fetchList}
        closeModal={() => setReplicatorNameDelete(null)}
        type={InstanceType.app}
      />
    </div>
  );
};

const Deployed = withStyles(styles)(DeployedView);
export default Deployed;
