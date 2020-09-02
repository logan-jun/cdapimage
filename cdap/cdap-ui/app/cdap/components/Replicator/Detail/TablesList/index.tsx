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
import { detailContextConnect, IDetailContext } from 'components/Replicator/Detail';
import { generateTableKey } from 'components/Replicator/utilities';
import { Map } from 'immutable';
import { MyReplicatorApi } from 'api/replicator';
import { getCurrentNamespace } from 'services/NamespaceStore';
import TextField from '@material-ui/core/TextField';
import InputAdornment from '@material-ui/core/InputAdornment';
import Search from '@material-ui/icons/Search';
import debounce from 'lodash/debounce';

const styles = (): StyleRules => {
  return {
    root: {
      '& > .grid-wrapper': {
        height: '100%',

        '& .grid.grid-container.grid-compact': {
          maxHeight: '500px',

          '& .grid-row': {
            gridTemplateColumns: '150px 2fr 1fr',
          },
        },
      },
    },

    subtitle: {
      marginTop: '10px',
      marginBottom: '10px',
      display: 'grid',
      gridTemplateColumns: '50% 50%',
      alignItems: 'center',

      '& > div:last-child': {
        textAlign: 'right',
      },
    },
    search: {
      width: '250px',

      '& input': {
        paddingTop: '10px',
        paddingBottom: '10px',
      },
    },
  };
};

const TablesListView: React.FC<IDetailContext & WithStyles<typeof styles>> = ({
  classes,
  name,
  tables,
  columns,
  offsetBasePath,
}) => {
  const [statusMap, setStatusMap] = React.useState(Map());
  const [filteredTables, setFilteredTables] = React.useState(tables.toList());
  const [search, setSearch] = React.useState('');

  function handleSearch(e) {
    setSearch(e.target.value);
  }

  const filterTableBySearch = debounce(() => {
    if (!search || search.length === 0) {
      setFilteredTables(tables.toList());
      return;
    }

    const filteredList = tables
      .filter((row) => {
        const normalizedTable = row.get('table').toLowerCase();
        const normalizedSearch = search.toLowerCase();

        return normalizedTable.indexOf(normalizedSearch) !== -1;
      })
      .toList();

    setFilteredTables(filteredList);
  }, 300);

  // handle search query change
  React.useEffect(filterTableBySearch, [search, tables]);

  React.useEffect(() => {
    if (!offsetBasePath || offsetBasePath.length === 0) {
      return;
    }

    const params = {
      namespace: getCurrentNamespace(),
    };

    const body = {
      name,
      offsetBasePath,
    };

    // TODO: optimize polling
    // Don't poll when status is not running
    const statusPoll$ = MyReplicatorApi.pollTableStatus(params, body).subscribe((res) => {
      const tableStatus = res.tables;

      let status = Map();
      tableStatus.forEach((tableInfo) => {
        const tableKey = generateTableKey(tableInfo);
        status = status.set(tableKey, tableInfo.state);
      });

      setStatusMap(status);
    });

    return () => {
      statusPoll$.unsubscribe();
    };
  }, [offsetBasePath]);

  return (
    <div className={classes.root}>
      <div className={classes.subtitle}>
        <div>
          <strong>{tables.size}</strong> tables to be replicated
        </div>
        <div>
          <TextField
            className={classes.search}
            value={search}
            onChange={handleSearch}
            variant="outlined"
            placeholder="Search by table name"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
            }}
          />
        </div>
      </div>

      <div className="grid-wrapper">
        <div className="grid grid-container grid-compact">
          <div className="grid-header">
            <div className="grid-row">
              <div>Status</div>
              <div>Table name</div>
              <div>Selected columns</div>
            </div>
          </div>

          <div className="grid-body">
            {filteredTables.toList().map((row) => {
              const tableKey = generateTableKey(row);

              const tableColumns = columns.get(tableKey);
              const numColumns = tableColumns ? tableColumns.size : 0;

              return (
                <div className="grid-row" key={tableKey.toString()}>
                  <div>{statusMap.get(tableKey) || '--'}</div>
                  <div>{row.get('table')}</div>
                  <div>{numColumns === 0 ? 'All' : numColumns}</div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

const StyledTablesList = withStyles(styles)(TablesListView);
const TablesList = detailContextConnect(StyledTablesList);
export default TablesList;
